// API地址配置文件
export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

// API路径枚举
export const API_PATHS = {
  // 认证相关
  LOGIN: '/auth/login',
  REGISTER: '/auth/register',
  
  // 业务相关
  UPLOAD_EXCEL: '/api/upload-excel',
  UPLOAD_EXCEL_LANGCHAIN: '/api/upload-excel-langchain',
  GET_TASKS: '/api/tasks',
  GET_TASK_RESULTS: '/api/task/:taskId/results'
};

// 构建完整API地址
export const buildApiUrl = (path, params = {}) => {
  let url = `${API_BASE_URL}${path}`;
  
  // 替换路径中的参数
  Object.entries(params).forEach(([key, value]) => {
    url = url.replace(`:${key}`, value);
  });
  
  return url;
};
