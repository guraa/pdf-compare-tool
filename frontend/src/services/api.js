import axios from 'axios';

// Create an axios instance with default config
const api = axios.create({
  baseURL: '/api', // Base URL for all API requests
  headers: {
    'Content-Type': 'application/json'
  },
  // Add timeout to prevent indefinite waiting
  timeout: 30000 // 30 seconds
});

// Add request interceptor for error handling
api.interceptors.request.use(
  config => {
    // Log request for debugging
    console.log(`API Request: ${config.method.toUpperCase()} ${config.url}`, config);
    return config;
  },
  error => {
    console.error('API Request Error:', error);
    return Promise.reject(error);
  }
);

// Add response interceptor for error handling
api.interceptors.response.use(
  response => {
    // Log successful responses for debugging
    console.log('API Response:', response.status, response.statusText);
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

// API test function to check connectivity
export const testAPIConnection = async () => {
  try {
    const response = await api.get('/config');
    console.log('API connection test successful:', response);
    return response;
  } catch (error) {
    console.error('API connection test failed:', error);
    throw error;
  }
};

// API functions
export const getAppConfiguration = async () => {
  const response = await api.get('/config');
  return response.data;
};

// PDF upload functions
export const uploadPDF = async (file) => {
  const formData = new FormData();
  formData.append('file', file);
  
  const response = await api.post('/pdfs/upload', formData, {
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
  // Use a longer timeout for this specific request as it might take time
  const response = await api.get(`/pdfs/comparison/${comparisonId}`, {
    timeout: 60000 // 60 seconds timeout for comparison results
  });
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
    responseType: 'blob',
    timeout: 60000 // 60 seconds timeout for report generation
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

// Utility function to check if a comparison is ready
// This can be used for polling without fetching the full result
export const checkComparisonStatus = async (comparisonId) => {
  try {
    const response = await api.head(`/pdfs/comparison/${comparisonId}`);
    return response.status === 200;
  } catch (error) {
    return false;
  }
};

export default api;