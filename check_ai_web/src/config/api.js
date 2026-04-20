export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const API_PATHS = {
  LOGIN: '/auth/login',
  REGISTER: '/auth/register',

  UPLOAD_EXCEL: '/api/upload-excel',
  UPLOAD_EXCEL_LANGCHAIN: '/api/upload-excel-langchain',
  GET_TASKS: '/api/tasks',
  GET_TASK_RESULTS: '/api/task/:taskId/results',
  CANCEL_TASK: '/api/task/:taskId/cancel',
  RETRY_TASK: '/api/task/:taskId/retry',
  GET_TASK_REVIEW: '/api/task/:taskId/review',
  SAVE_TASK_REVIEW: '/api/task/:taskId/review',
  LIST_TASK_REVIEWS: '/api/task-reviews',

  GET_LOGISTICS_LIST: '/logistics/list',
  SUBMIT_LOCAL_DATA: '/api/submit-local-data',

  AI_CHAT: '/ai/chat',
  AI_CHAT_RAG: '/ai/chat/rag',
  AI_DOCUMENT_UPLOAD: '/ai/documents/upload',
  AI_DOCUMENT_SEARCH: '/ai/documents/search',
  AI_HISTORY: '/ai/history'
};

export const buildApiUrl = (path, params = {}) => {
  let url = `${API_BASE_URL}${path}`;
  Object.entries(params).forEach(([key, value]) => {
    url = url.replace(`:${key}`, value);
  });
  return url;
};
