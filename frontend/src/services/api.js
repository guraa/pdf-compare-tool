import axios from 'axios';

// Create an axios instance with default config
const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json'
  }
});

// Add request interceptor for error handling
api.interceptors.request.use(
  config => {
    // You can add authentication tokens here if needed
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

// Add response interceptor for error handling
api.interceptors.response.use(
  response => {
    return response;
  },
  error => {
    // Handle API errors gracefully
    console.error('API Error:', error);
    
    if (error.response) {
      // The request was made and the server responded with a status code
      // that falls out of the range of 2xx
      console.error('Response data:', error.response.data);
      console.error('Response status:', error.response.status);
    } else if (error.request) {
      // The request was made but no response was received
      console.error('No response received:', error.request);
    } else {
      // Something happened in setting up the request that triggered an Error
      console.error('Request error:', error.message);
    }
    
    return Promise.reject(error);
  }
);

// API functions
export const getAppConfiguration = async () => {
  const response = await api.get('/config');
  return response.data;
};

// PDF upload functions
export const uploadPDF = async (file) => {
  const formData = new FormData();
  formData.append('file', file);
  
  const response = await axios.post('/api/pdfs/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
  
  return response.data;
};

// Comparison functions
export const compareDocuments = async (baseFileId, compareFileId, options = {}) => {
  const response = await api.post('/pdfs/compare', {
    baseFileId,
    compareFileId,
    options
  });
  
  return response.data;
};

export const getComparisonResult = async (comparisonId) => {
  const response = await api.get(`/pdfs/comparison/${comparisonId}`);
  return response.data;
};

export const getComparisonDetails = async (comparisonId, page, filters = {}) => {
  const response = await api.get(`/pdfs/comparison/${comparisonId}/page/${page}`, {
    params: filters
  });
  return response.data;
};

// Document retrieval functions
export const getDocumentPage = async (fileId, page, options = {}) => {
  const response = await api.get(`/pdfs/document/${fileId}/page/${page}`, {
    params: options,
    responseType: 'blob'
  });
  return response.data;
};

// Report generation functions
export const generateReport = async (comparisonId, format = 'pdf', options = {}) => {
  const response = await api.post(`/pdfs/comparison/${comparisonId}/report`, {
    format,
    options
  }, {
    responseType: 'blob'
  });
  
  return response.data;
};

// Helper functions for working with the API
export const downloadBlob = (blob, filename) => {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

export default api;