"""
基于 LangGraph 的多Agent工作流版（参考 Demo1/多Agent协作系统）

目标：
- 不修改源文件：Demo1/多智能体/AgentsApp.py
- 将 Excel解析 -> 下载图片+OCR -> 重量一致性校验(LLM) 拆为多节点工作流
- Coordinator(协调员) 负责状态路由，节点执行后回到 Coordinator
- 保留 Flask API：/health 与 /api/process（兼容文件上传或传 excel_path）

修复记录（v2）：
1. [BUG] ndarray JSON序列化失败：_json_default 补充 numpy 类型处理；
   paddle_ocr_images 不再存储含 ndarray 的 raw 字段，改为只保留文本结果。
2. [BUG] OCRResult 类型未被识别：_extract_text_lines_from_ocr_result 优先
   通过 hasattr 访问 rec_texts 属性，兼容 PaddleX OCRResult 对象。
3. [BUG] 图片文件名碰撞：OcrAgent 每行使用独立临时子目录，避免下一行
   下载时 clear_folder 清掉当前行图片，导致所有行都 OCR 同一张图。
4. [OPT] download_images_from_urls 不再在函数内部 clear_folder，由调用方
   负责传入独立目录，确保并发安全。
5. [OPT] _serialize_value 统一处理 numpy 标量，避免 DataFrame 序列化时遗漏。

依赖说明（按需安装）：
- Flask, werkzeug
- pandas（读 Excel）
- xlrd<2（若读 .xls）
- requests, paddleocr
- langgraph
- langchain_openai（Think 节点调用 LLM，可选）
- numpy（通常随 paddleocr/pandas 自动安装）

LLM 配置（通过环境变量）：
- DEEPSEEK_API_KEY
- DEEPSEEK_BASE_URL（默认 https://api.deepseek.com/v1 ）
- DEEPSEEK_MODEL（默认 deepseek-chat）
"""

from __future__ import annotations

import json
import os
import re
import shutil
import traceback
import uuid
from datetime import datetime
from typing import Any, Dict, List, Optional, TypedDict

from flask import Flask, request
from werkzeug.utils import secure_filename

# -------------------------
# Optional deps (graceful)
# -------------------------
try:
    import pandas as pd  # type: ignore
except ImportError:
    pd = None  # type: ignore

try:
    import requests  # type: ignore
except ImportError:
    requests = None  # type: ignore

try:
    from paddleocr import PaddleOCR  # type: ignore
except ImportError:
    PaddleOCR = None  # type: ignore

try:
    from langgraph.graph import StateGraph, END  # type: ignore
except ImportError:
    StateGraph = None  # type: ignore
    END = None  # type: ignore

try:
    from langchain_openai import ChatOpenAI  # type: ignore
except ImportError:
    ChatOpenAI = None  # type: ignore

# numpy 可选（随 paddleocr 安装），用于序列化兜底
try:
    import numpy as np  # type: ignore
    _HAS_NUMPY = True
except ImportError:
    np = None  # type: ignore
    _HAS_NUMPY = False

from urllib.parse import urlparse


# ====================
# Flask basic config
# ====================
UPLOAD_FOLDER = "./uploads"
TEMP_IMAGES_FOLDER = "./temp_images"
ALLOWED_EXTENSIONS = {"xls", "xlsx"}
MAX_FILE_SIZE = 16 * 1024 * 1024


def create_app() -> Flask:
    app = Flask(__name__)
    app.config["JSON_AS_ASCII"] = False
    app.config["UPLOAD_FOLDER"] = UPLOAD_FOLDER
    app.config["MAX_CONTENT_LENGTH"] = MAX_FILE_SIZE

    os.makedirs(UPLOAD_FOLDER, exist_ok=True)
    os.makedirs(TEMP_IMAGES_FOLDER, exist_ok=True)

    return app


app = create_app()


# ====================
# Utils
# ====================
def clear_folder(folder_path: str) -> None:
    if not os.path.exists(folder_path):
        return
    for filename in os.listdir(folder_path):
        file_path = os.path.join(folder_path, filename)
        try:
            if os.path.isfile(file_path) or os.path.islink(file_path):
                os.unlink(file_path)
            elif os.path.isdir(file_path):
                shutil.rmtree(file_path)
        except Exception:
            pass


def allowed_file(filename: str) -> bool:
    return "." in filename and filename.rsplit(".", 1)[1].lower() in ALLOWED_EXTENSIONS


def _json_default(o: Any) -> Any:
    """
    JSON 安全序列化：
    - datetime -> ISO 字符串
    - numpy ndarray -> list（递归）
    - numpy 标量 -> Python 原生 int / float
    - 其余无法序列化的对象 -> str(o)

    FIX #1: 补充 numpy 类型处理，防止 OCR 结果中的 ndarray 导致序列化失败。
    """
    if isinstance(o, datetime):
        return o.isoformat()
    if _HAS_NUMPY:
        if isinstance(o, np.ndarray):
            return o.tolist()
        if isinstance(o, np.integer):
            return int(o)
        if isinstance(o, (np.floating, np.float64, np.float32)):
            return float(o)
        if isinstance(o, np.bool_):
            return bool(o)
    try:
        return str(o)
    except Exception:
        return repr(o)


def create_response(data: Any, status_code: int):
    return app.response_class(
        response=json.dumps(data, ensure_ascii=False, indent=2, default=_json_default),
        status=status_code,
        mimetype="application/json; charset=utf-8",
    )


def _normalize_path(p: str) -> str:
    p = str(p).strip().replace("\n", "").replace("\r", "")
    p = p.strip('"').strip("'")
    return os.path.normpath(p)


# ====================
# OCR components
# ====================
_ocr_instance = None


def get_ocr():
    global _ocr_instance
    if _ocr_instance is not None:
        return _ocr_instance

    if PaddleOCR is None:
        raise RuntimeError(
            "Missing dependency: paddleocr. Install: python -m pip install paddleocr"
        )

    device = os.getenv("PADDLEOCR_DEVICE", "cpu")
    print(f"初始化PaddleOCR，使用设备: {device}")

    init_kwargs = dict(
        use_doc_orientation_classify=False,
        use_doc_unwarping=False,
        use_textline_orientation=False,
    )

    for try_device in (device, "cpu"):
        try:
            _ocr_instance = PaddleOCR(device=try_device, **init_kwargs)
            print(f"PaddleOCR 初始化成功（device={try_device}）")
            return _ocr_instance
        except Exception as e:
            print(f"PaddleOCR device={try_device} 初始化失败: {e}")

    raise RuntimeError("PaddleOCR 初始化失败，CPU 模式也不可用")


def download_images_from_urls(
    url_input: str,
    save_dir: str = TEMP_IMAGES_FOLDER,
    clear_before: bool = False,
) -> List[str]:
    """
    下载 URL 列表中的图片到 save_dir。

    FIX #3: 移除函数内部的强制 clear_folder，改为可选参数 clear_before。
    OcrAgent 会为每一行传入独立的子目录，彻底避免多行之间互相清除图片文件。
    """
    if requests is None:
        raise RuntimeError(
            "Missing dependency: requests. Install: python -m pip install requests"
        )

    if clear_before:
        clear_folder(save_dir)
    os.makedirs(save_dir, exist_ok=True)

    url_input = (url_input or "").strip()
    urls = [u.strip() for u in url_input.split(",") if u.strip()]
    if not urls:
        return []

    saved_paths: List[str] = []
    for i, url in enumerate(urls):
        try:
            parsed = urlparse(url)
            if not parsed.scheme or not parsed.netloc:
                continue

            resp = requests.get(url, timeout=15)
            resp.raise_for_status()

            content_type = (resp.headers.get("content-type", "") or "").lower()
            if "jpeg" in content_type or "jpg" in content_type:
                ext = ".jpg"
            elif "png" in content_type:
                ext = ".png"
            elif "gif" in content_type:
                ext = ".gif"
            else:
                ext = os.path.splitext(parsed.path)[1].lower()
                if ext not in {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"}:
                    ext = ".jpg"

            filename = f"img_{i:03d}{ext}"
            filepath = os.path.join(save_dir, filename)
            with open(filepath, "wb") as f:
                f.write(resp.content)

            saved_paths.append(os.path.abspath(filepath))
        except Exception as e:
            print(f"下载图片失败 [{url}]: {e}")
            continue

    return saved_paths


def _extract_text_lines_from_ocr_result(result: Any) -> List[str]:
    """
    从 PaddleOCR/PaddleX 的返回结构中提取文本行。

    FIX #2: 优先通过 hasattr 访问 OCRResult.rec_texts 属性，
    避免 isinstance(item, dict) 判断对 OCRResult 对象失效。
    """
    lines: List[str] = []
    try:
        print(f"OCR结果类型: {type(result)}")
        print(f"OCR结果内容预览: {str(result)[:200]}...")

        if not isinstance(result, list):
            # 处理单个对象
            result = [result]

        for item in result:
            print(f"处理项目类型: {type(item)}")

            # 1) PaddleX OCRResult 对象：优先通过属性访问
            if hasattr(item, "rec_texts"):
                rec_texts = item.rec_texts
                if isinstance(rec_texts, list):
                    lines.extend([str(x) for x in rec_texts if str(x).strip()])
                continue

            # 2) dict（部分版本 PaddleOCR 返回 dict）
            if isinstance(item, dict):
                # 2a) 带 rec_texts key
                rec_texts = item.get("rec_texts")
                if isinstance(rec_texts, list):
                    lines.extend([str(x) for x in rec_texts if str(x).strip()])
                    continue
                # 2b) 旧版结构：[[[points], (text, conf)], ...]
                inner = item.get("data") or item.get("result") or []
                for sub in inner:
                    if isinstance(sub, (list, tuple)) and len(sub) >= 2:
                        text_part = sub[-1]
                        if isinstance(text_part, (list, tuple)) and text_part:
                            t = str(text_part[0]).strip()
                            if t:
                                lines.append(t)
                        elif isinstance(text_part, str) and text_part.strip():
                            lines.append(text_part.strip())
                continue

            # 3) 旧版 PaddleOCR：list of list → [[[points], (text, conf)], ...]
            if isinstance(item, (list, tuple)):
                for sub in item:
                    if isinstance(sub, (list, tuple)) and len(sub) >= 2:
                        text_part = sub[-1]
                        if isinstance(text_part, (list, tuple)) and text_part:
                            t = str(text_part[0]).strip()
                            if t:
                                lines.append(t)
                        elif isinstance(text_part, str) and text_part.strip():
                            lines.append(text_part.strip())
                continue

            # 4) 直接字符串
            if isinstance(item, str) and item.strip():
                lines.append(item.strip())

    except Exception as e:
        print(f"提取OCR文本时出错: {e}")

    if not lines:
        s = str(result).strip()
        if s:
            lines.append(s)

    print(f"提取到的文本行数: {len(lines)}")
    return lines


def paddle_ocr_images(image_paths: List[str]) -> Dict[str, Any]:
    """
    FIX #1: 不再把含 ndarray 的 raw 字段写入返回结果，
    只保留 text_lines（纯字符串列表），彻底避免 JSON 序列化失败。
    """
    try:
        ocr = get_ocr()
    except Exception as e:
        return {"items": [], "error": f"OCR初始化失败: {str(e)}"}

    all_results: List[Dict[str, Any]] = []
    for img_path in image_paths:
        if not os.path.exists(img_path):
            all_results.append(
                {"image_path": img_path, "error": "文件不存在", "text_lines": []}
            )
            continue
        try:
            print(f"开始OCR处理图片: {img_path}")
            res = ocr.predict(img_path)
            text_lines = _extract_text_lines_from_ocr_result(res)
            # 只存储纯文本，不存储含 ndarray 的 raw 字段
            all_results.append(
                {
                    "image_path": img_path,
                    "text_lines": text_lines,
                }
            )
            print(f"图片 {img_path} 处理完成，提取到 {len(text_lines)} 行文本")
        except Exception as e:
            print(f"处理图片 {img_path} 时出错: {e}")
            all_results.append(
                {"image_path": img_path, "error": str(e), "text_lines": []}
            )
    return {"items": all_results}


# ====================
# Excel parsing
# ====================
def _split_and_clean_urls(cell: Any) -> List[str]:
    if cell is None:
        return []
    if pd is not None and isinstance(cell, float) and pd.isna(cell):
        return []
    s = str(cell)
    if not s or s.lower() == "nan":
        return []
    out: List[str] = []
    for p in s.split(","):
        u = p.strip()
        if u and u.lower() != "null":
            out.append(u)
    return out


def _serialize_value(value: Any) -> Any:
    """
    FIX #5: 补充 numpy 标量处理，保证 DataFrame 序列化完整覆盖所有类型。
    """
    if pd is not None:
        try:
            if pd.isna(value):
                return None
        except (TypeError, ValueError):
            pass
    if isinstance(value, datetime):
        return value.strftime("%Y-%m-%d %H:%M:%S")
    if hasattr(value, "strftime"):
        return value.strftime("%Y-%m-%d %H:%M:%S")
    if _HAS_NUMPY:
        if isinstance(value, np.ndarray):
            return value.tolist()
        if isinstance(value, np.integer):
            return int(value)
        if isinstance(value, (np.floating, np.float64, np.float32)):
            return float(value)
        if isinstance(value, np.bool_):
            return bool(value)
    if isinstance(value, (list, tuple)):
        return [_serialize_value(item) for item in value]
    return value


def excel_analysis(excel_path: str) -> List[Dict[str, Any]]:
    if pd is None:
        raise RuntimeError(
            "Missing dependency: pandas. Install: python -m pip install -U pandas"
        )

    excel_path = _normalize_path(excel_path)
    if not os.path.exists(excel_path):
        raise FileNotFoundError(f"Excel 文件不存在: {excel_path}")

    df = pd.read_excel(excel_path)

    excel_base_raw = df.iloc[:, 0:13].values.tolist()
    excel_pull = df.iloc[:, 13].apply(_split_and_clean_urls).tolist()
    excel_push = df.iloc[:, 14].apply(_split_and_clean_urls).tolist()

    excel_base = [
        [_serialize_value(cell) for cell in row] for row in excel_base_raw
    ]

    return [
        {
            "excel_base": excel_base[i],
            "excel_pull": excel_pull[i],
            "excel_push": excel_push[i],
        }
        for i in range(len(excel_base))
    ]


# ====================
# LLM (Think node)
# ====================
def get_llm():
    if ChatOpenAI is None:
        raise RuntimeError(
            "Missing dependency: langchain_openai. "
            "Install: python -m pip install -U langchain-openai"
        )
    api_key = os.getenv("DEEPSEEK_API_KEY")
    base_url = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
    model = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
    if not api_key:
        raise RuntimeError("Missing env: DEEPSEEK_API_KEY")
    return ChatOpenAI(model=model, api_key=api_key, base_url=base_url)


def build_think_prompt(records_json: str) -> str:
    return f"""你是一个物流磅单票据异常检查专家。

输入是JSON（包含excel_base、excel_pull、excel_push，pull/push 已由OCR输出文字行）：
{records_json}

你是物流磅单数据校验专家，负责校验Excel标准数据与装卸货磅单OCR数据的重量一致性。

校验流程（核心规则）：
1) 关键字段完整性：Excel必需装货重量/卸货重量；OCR必需毛重/皮重或净重。缺失则标记严重异常。
2) 重量逻辑合规性：毛重大于皮重；净重大于0；净重=毛重-皮重，允许正负0.01吨误差。
3) 重量一致性：偏差5%到10%标记异常；偏差3‰到5%标记提醒。
4) 单位与格式：公斤->吨/1000；去千位分隔符；统一保留2位小数。

输出要求：
- 输出纯文字报告（不要输出JSON）。
- 条目数等于输入条目数。
- 正常条目一行；异常条目要展开对比与建议。
"""


# ====================
# LangGraph Multi-Agent Workflow
# ====================
class LogisticsState(TypedDict):
    excel_path: str
    excel_rows: List[Dict[str, Any]]
    ocr_rows: List[Dict[str, Any]]
    report_text: str
    warnings: List[str]
    errors: List[str]
    current_agent: str
    next_action: str
    is_complete: bool


def create_initial_state(excel_path: str) -> LogisticsState:
    return LogisticsState(
        excel_path=excel_path,
        excel_rows=[],
        ocr_rows=[],
        report_text="",
        warnings=[],
        errors=[],
        current_agent="coordinator",
        next_action="excel",
        is_complete=False,
    )


class CoordinatorAgent:
    def decide_next_action(self, state: LogisticsState) -> str:
        if state.get("is_complete") or state.get("errors"):
            return "complete"
        if not state.get("excel_rows"):
            return "excel"
        if not state.get("ocr_rows"):
            return "ocr"
        if not state.get("report_text"):
            return "think"
        return "complete"


class ExcelAgent:
    def run(self, state: LogisticsState) -> LogisticsState:
        try:
            state["excel_rows"] = excel_analysis(state["excel_path"])
            print(f"Excel解析完成，共 {len(state['excel_rows'])} 行")
        except Exception as e:
            print(f"Excel解析失败: {e}")
            state["errors"].append(str(e))
            state["is_complete"] = True
        return state


class OcrAgent:
    def _ocr_urls(self, urls: List[str], row_idx: int, side: str) -> List[Dict[str, Any]]:
        """
        FIX #3: 每次调用使用独立子目录（row_idx + side + uuid），
        避免多行并发/顺序处理时目录清除导致文件丢失。
        """
        if not urls:
            return []

        unique_dir = os.path.join(
            TEMP_IMAGES_FOLDER,
            f"row{row_idx:04d}_{side}_{uuid.uuid4().hex[:8]}",
        )
        os.makedirs(unique_dir, exist_ok=True)

        try:
            url_input = ",".join(urls)
            image_paths = download_images_from_urls(
                url_input, save_dir=unique_dir, clear_before=False
            )
            if not image_paths:
                return [{"url_input": url_input, "error": "download_failed_or_empty", "items": []}]
            return [paddle_ocr_images(image_paths)]
        finally:
            # 处理完毕后清理临时目录，避免磁盘堆积
            try:
                shutil.rmtree(unique_dir, ignore_errors=True)
            except Exception:
                pass

    def run(self, state: LogisticsState) -> LogisticsState:
        try:
            ocr_rows: List[Dict[str, Any]] = []
            for idx, row in enumerate(state.get("excel_rows", [])):
                pull_urls = [str(u) for u in (row.get("excel_pull") or [])]
                push_urls = [str(u) for u in (row.get("excel_push") or [])]

                print(f"行 {idx}: pull URL数={len(pull_urls)}, push URL数={len(push_urls)}")

                pull_ocr = self._ocr_urls(pull_urls, idx, "pull")
                push_ocr = self._ocr_urls(push_urls, idx, "push")

                ocr_rows.append(
                    {
                        "excel_base": row.get("excel_base"),
                        "excel_pull": pull_ocr,
                        "excel_push": push_ocr,
                    }
                )
            state["ocr_rows"] = ocr_rows
            print(f"OCR处理完成，共 {len(ocr_rows)} 行")
        except Exception as e:
            print(f"OCR处理失败: {e}")
            state["errors"].append(str(e))
            state["is_complete"] = True
        return state


class ThinkAgent:
    def run(self, state: LogisticsState) -> LogisticsState:
        try:
            llm = get_llm()
            # 序列化时使用 _json_default 确保 numpy 等类型安全
            input_json = json.dumps(
                state.get("ocr_rows", []),
                ensure_ascii=False,
                default=_json_default,
            )
            prompt = build_think_prompt(input_json)
            resp = llm.invoke(prompt)
            state["report_text"] = getattr(resp, "content", str(resp))
            print("LLM分析完成")
        except Exception as e:
            print(f"LLM分析失败: {e}")
            state["errors"].append(str(e))
            state["is_complete"] = True
        return state


class LogisticsWorkflow:
    def __init__(self):
        self.coordinator = CoordinatorAgent()
        self.excel_agent = ExcelAgent()
        self.ocr_agent = OcrAgent()
        self.think_agent = ThinkAgent()
        self.graph = self._build_graph()

    def _build_graph(self):
        if StateGraph is None or END is None:
            raise RuntimeError(
                "Missing dependency: langgraph. Install: python -m pip install -U langgraph"
            )

        workflow = StateGraph(LogisticsState)
        workflow.add_node("coordinator", self._coordinator_node)
        workflow.add_node("excel", self._excel_node)
        workflow.add_node("ocr", self._ocr_node)
        workflow.add_node("think", self._think_node)

        workflow.set_entry_point("coordinator")
        workflow.add_conditional_edges(
            "coordinator",
            self._route_from_coordinator,
            {
                "excel": "excel",
                "ocr": "ocr",
                "think": "think",
                "complete": END,
            },
        )

        workflow.add_edge("excel", "coordinator")
        workflow.add_edge("ocr", "coordinator")
        workflow.add_edge("think", "coordinator")
        return workflow.compile()

    def _coordinator_node(self, state: LogisticsState) -> LogisticsState:
        state["current_agent"] = "coordinator"
        state["next_action"] = self.coordinator.decide_next_action(state)
        print(f"Coordinator 决策 -> next_action: {state['next_action']}")
        return state

    def _excel_node(self, state: LogisticsState) -> LogisticsState:
        state["current_agent"] = "excel"
        return self.excel_agent.run(state)

    def _ocr_node(self, state: LogisticsState) -> LogisticsState:
        state["current_agent"] = "ocr"
        return self.ocr_agent.run(state)

    def _think_node(self, state: LogisticsState) -> LogisticsState:
        state["current_agent"] = "think"
        return self.think_agent.run(state)

    def _route_from_coordinator(self, state: LogisticsState) -> str:
        return state["next_action"]

    def run(self, excel_path: str) -> LogisticsState:
        initial_state = create_initial_state(excel_path)
        final_state = self.graph.invoke(initial_state)
        if not final_state.get("errors"):
            final_state["is_complete"] = True
        return final_state


# 单例工作流（懒加载，避免 import 时因缺依赖崩）
_workflow: Optional[LogisticsWorkflow] = None


def get_workflow() -> LogisticsWorkflow:
    global _workflow
    if _workflow is None:
        _workflow = LogisticsWorkflow()
    return _workflow


# ====================
# API routes
# ====================
@app.route("/health", methods=["GET"])
def health_check():
    return create_response({"status": "healthy", "timestamp": datetime.now().isoformat()}, 200)


@app.route("/api/process", methods=["POST"])
def process_document():
    try:
        print("收到API请求处理文档")
        excel_path: Optional[str] = None

        if "file" in request.files:
            file = request.files["file"]
            if not file.filename:
                return create_response({"success": False, "error": "未选择文件"}, 400)
            if not allowed_file(file.filename):
                return create_response({"success": False, "error": "不支持的文件类型"}, 400)

            filename = secure_filename(file.filename)
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"{timestamp}_{filename}"
            excel_path = os.path.join(app.config["UPLOAD_FOLDER"], filename)
            file.save(excel_path)
            print(f"文件上传保存至: {excel_path}")

        elif request.is_json:
            data = request.get_json()
            if not data or "excel_path" not in data:
                return create_response(
                    {"success": False, "error": "请提供文件或excel_path参数"}, 400
                )
            excel_path = _normalize_path(data["excel_path"])
            print(f"接收到excel_path参数: {excel_path}")
        else:
            return create_response(
                {"success": False, "error": "请上传文件或提供excel_path参数"}, 400
            )

        excel_path = _normalize_path(excel_path)
        print(f"处理Excel文件路径: {excel_path}")

        if not os.path.exists(excel_path):
            return create_response(
                {"success": False, "error": f"文件不存在: {excel_path}"}, 404
            )

        wf = get_workflow()
        print("开始执行工作流...")
        state = wf.run(excel_path)

        if state.get("errors"):
            print(f"工作流执行出错: {state['errors']}")
            # state 中可能含 ndarray，序列化时由 _json_default 兜底
            return create_response(
                {"success": False, "error": state["errors"]}, 500
            )

        print("工作流执行成功")
        return create_response(
            {
                "success": True,
                "data": state.get("report_text", ""),
                "row_count": len(state.get("ocr_rows", [])),
            },
            200,
        )

    except Exception as e:
        print(f"API处理异常: {e}")
        print(traceback.format_exc())
        return create_response(
            {"success": False, "error": str(e), "traceback": traceback.format_exc()},
            500,
        )


@app.errorhandler(413)
def request_entity_too_large(_error):
    return create_response({"success": False, "error": "文件过大，上限16MB"}, 413)


if __name__ == "__main__":
    # 可选：提前加载 OCR 模型（若未装 paddleocr，不阻断启动）
    try:
        get_ocr()
    except Exception as e:
        print(f"OCR 预加载跳过: {e}")
    app.run(host="0.0.0.0", port=5000, debug=True, use_reloader=False)