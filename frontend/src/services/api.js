import axios from 'axios';
import CircuitBreakerService from './CircuitBreakerService';

// Create circuit breakers for different API endpoints
const circuitBreakers = {
  default: new CircuitBreakerService({
    threshold: 5, 
    resetTimeout: 30000, // 30 seconds
    debug: false // Set to true to see debug logs
  }),
  documentPairs: new CircuitBreakerService({
    threshold: 3,
    resetTimeout: 60000, // 60 seconds for document pairs endpoint
    debug: false
  })
};

// Create an axios instance with default config
const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json'
  },
  timeout: 120000 // 2 minutes
});

// Add request interceptor for error handling and circuit breaker
api.interceptors.request.use(
  config => {
    // Determine which circuit breaker to use based on the endpoint
    const endpoint = config.url.split('/')[1] || 'default';
    const cbKey = endpoint === 'pdfs' && config.url.includes('documents') ? 'documentPairs' : 'default';
    const breaker = circuitBreakers[cbKey] || circuitBreakers.default;
    
    // Add circuit breaker key to config for the response interceptor
    config.circuitBreakerKey = cbKey;
    
    // Check if circuit breaker is open
    if (breaker.isOpen()) {
      return Promise.reject({
        circuitBreakerOpen: true,
        message: 'Service temporarily unavailable due to high load. Please try again later.'
      });
    }
    
    // Add a request counter to track retries
    config.retryCount = config.retryCount || 0;
    
    return config;
  },
  error => {
    console.error('API Request Error:', error);
    return Promise.reject(error);
  }
);

// Add response interceptor for error handling and circuit breaker
api.interceptors.response.use(
  response => {
    // Get the circuit breaker for this request
    const cbKey = response.config.circuitBreakerKey || 'default';
    const breaker = circuitBreakers[cbKey] || circuitBreakers.default;
    
    // Record success in circuit breaker
    breaker.success();
    
    return response;
  },
  error => {
    // Handle circuit breaker rejection
    if (error.circuitBreakerOpen) {
      console.error('Circuit breaker is open. Request rejected.');
      return Promise.reject(error);
    }
    
    // Get the circuit breaker for this request
    const cbKey = error.config?.circuitBreakerKey || 'default';
    const breaker = circuitBreakers[cbKey] || circuitBreakers.default;
    
    // Check if it's a timeout or network error
    const isNetworkError = !error.response && (
      error.code === 'ERR_NETWORK' || 
      error.code === 'ECONNABORTED' || 
      error.message.includes('timeout')
    );
    
    // Record failure for network errors or 5xx responses
    if (isNetworkError || (error.response && error.response.status >= 500)) {
      breaker.failure();
    }
    
    // Add circuit breaker information to the error
    if (error.config) {
      error.circuitBreakerInfo = breaker.getStatus();
    }
    
    // No retry logic - removed as requested
    
    // For all other errors, reject the promise
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

export const compareDocuments = async (baseFileId, compareFileId, options = {}) => {
  // Always enable smart matching
  const requestOptions = { 
    ...options,
    smartMatching: true
  };
  
  const response = await api.post('/pdfs/compare', {
    baseFileId,
    compareFileId,
    options: requestOptions
  });
  
  return response.data;
};

// Get overall comparison result
export const getComparisonResult = async (comparisonId) => {
  try {
    // Use a longer timeout for this specific request as it might take time
    const response = await api.get(`/pdfs/comparison/${comparisonId}`, {
      timeout: 120000, // 2 minutes timeout for comparison results
      validateStatus: function (status) {
        // Accept 202 Accepted as a valid response (still processing)
        return (status >= 200 && status < 300) || status === 202;
      }
    });
    
    // If status is 202, throw a "still processing" error that the retry logic can handle
    if (response.status === 202) {
      throw new Error("Comparison still processing");
    }
    
    return response.data;
  } catch (error) {
    // If our circuit breaker detected this request should be rejected
    if (error.circuitBreakerOpen) {
      throw new Error("Service temporarily unavailable due to high load. Please try again later.");
    }
    
    console.error("Error fetching comparison result:", error);
    throw error;
  }
};

// Get comparison details for a specific page
export const getComparisonDetails = async (comparisonId, page, filters = {}) => {
  if (!comparisonId || !page) {
    console.error("Missing required parameters for getComparisonDetails:", { comparisonId, page });
    throw new Error("ComparisonId and page are required");
  }
  
  try {
    // Convert filter objects to query parameters
    const params = {};
    
    // Always include all difference types if not specified
    if (!filters || !filters.differenceTypes || filters.differenceTypes.length === 0) {
      params.types = 'text,font,image,style,metadata,structure';
    } else {
      params.types = filters.differenceTypes.join(',');
    }
    
    if (filters && filters.minSeverity && filters.minSeverity !== 'all') {
      params.severity = filters.minSeverity;
    }
    
    if (filters && filters.searchTerm) {
      params.search = filters.searchTerm;
    }
    
    // Ensure page is a valid number
    const pageNumber = parseInt(page) || 1;
    
    // Debug logging
    console.log(`Making API request to: /pdfs/comparison/${comparisonId}/page/${pageNumber} with params:`, params);
    
    try {
      const response = await api.get(`/pdfs/comparison/${comparisonId}/page/${pageNumber}`, { 
        params,
        timeout: 120000,
        validateStatus: function (status) {
          return (status >= 200 && status < 300) || status === 202;
        }
      });
      
      // Check for processing status
      if (response.status === 202) {
        throw new Error("Comparison still processing");
      }
      
      // Ensure differences arrays exist
      if (!response.data.baseDifferences) {
        response.data.baseDifferences = [];
      }
      
      if (!response.data.compareDifferences) {
        response.data.compareDifferences = [];
      }
      
      return response.data;
    } catch (error) {
      // Pass through processing status
      if (error.message === "Comparison still processing") {
        throw error;
      }
      
      console.error("Error fetching page details:", error);
      throw error;
    }
  } catch (error) {
    console.error(`Error in getComparisonDetails for comparison ${comparisonId}, page ${page}:`, error);
    throw error;
  }
};

// Get document pairs for smart comparison
export const getDocumentPairs = async (comparisonId) => {
  try {
    const response = await api.get(`/pdfs/comparison/${comparisonId}/documents`, {
      timeout: 120000,
      validateStatus: function (status) {
        // Accept 202 Accepted as a valid response (still processing)
        return (status >= 200 && status < 300) || status === 202;
      }
    });
    
    // If status is 202, throw a "still processing" error that the retry logic can handle
    if (response.status === 202) {
      throw new Error("Document matching still processing");
    }
    
    return response.data;
  } catch (error) {
    // If our circuit breaker detected this request should be rejected
    if (error.circuitBreakerOpen) {
      throw new Error("Service temporarily unavailable due to high load. Please try again later.");
    }
    
    // Rethrow the original error
    throw error;
  }
};
  
// Get comparison result for a specific document pair
export const getDocumentPairResult = async (comparisonId, pairIndex) => {
  try {
    const response = await api.get(`/pdfs/comparison/${comparisonId}/documents/${pairIndex}`, {
      timeout: 120000, // 2 minutes timeout
      validateStatus: function (status) {
        // Accept 202 Accepted as a valid response (still processing)
        return (status >= 200 && status < 300) || status === 202;
      }
    });
    
    // If status is 202, throw a "still processing" error that the retry logic can handle
    if (response.status === 202) {
      throw new Error("Comparison still processing");
    }
    
    return response.data;
  } catch (error) {
    console.error(`Error fetching comparison result for document pair ${pairIndex}:`, error);
    
    // Handle circuit breaker errors
    if (error.circuitBreakerOpen) {
      // Return a special error that the UI can handle
      throw new Error("Service temporarily unavailable due to high load. Please try again later.");
    }
    
    throw error;
  }
};

// Get page details for a specific document in a pair
export const getDocumentPageDetails = async (comparisonId, pairIndex, pageNumber, filters = {}) => {
  // Convert filter objects to simple string parameters
  const params = {};
  
  // Always include all difference types if not specified
  if (!filters || !filters.differenceTypes || filters.differenceTypes.length === 0) {
    params.types = 'text,font,image,style,metadata,structure';
  } else {
    params.types = filters.differenceTypes.join(',');
  }
  
  if (filters && filters.minSeverity && filters.minSeverity !== 'all') {
    params.severity = filters.minSeverity;
  }
  
  if (filters && filters.searchTerm) {
    params.search = filters.searchTerm;
  }
  
  try {
    const response = await api.get(
      `/pdfs/comparison/${comparisonId}/documents/${pairIndex}/page/${pageNumber}`, 
      { 
        params,
        // Accept 404 responses
        validateStatus: function (status) {
          return status === 200 || status === 404 || status === 202;
        }
      }
    );
    
    // If we got a 404 response, return empty data with a message
    if (response.status === 404) {
      return {
        baseDifferences: [],
        compareDifferences: [],
        message: response.data?.error || "Page not found in document pair",
        maxPage: response.data?.maxPage || null
      };
    }
    
    // If status is 202, the comparison is still processing
    if (response.status === 202) {
      throw new Error("Comparison still processing");
    }
    
    // Add default empty arrays if missing
    if (!response.data.baseDifferences) {
      response.data.baseDifferences = [];
    }
    
    if (!response.data.compareDifferences) {
      response.data.compareDifferences = [];
    }
    
    return response.data;
  } catch (error) {
    // Handle circuit breaker errors
    if (error.circuitBreakerOpen) {
      return {
        baseDifferences: [],
        compareDifferences: [],
        message: "Service temporarily unavailable due to high load. Please try again later.",
        circuitBreakerOpen: true
      };
    }
    
    // If the error is "Comparison still processing", rethrow it
    if (error.message === "Comparison still processing") {
      throw error;
    }
    
    // For other errors, return a more descriptive error message
    return {
      baseDifferences: [],
      compareDifferences: [],
      message: `Error: ${error.message}`,
      error: error
    };
  }
};

// Document retrieval functions
export const getDocumentPage = async (fileId, page, options = {}) => {
  try {
    const response = await api.get(`/pdfs/document/${fileId}/page/${page}`, {
      params: options,
      responseType: 'blob',
      headers: {
        'Accept': 'image/png'  // Explicitly request PNG format
      }
    });
    return response.data;
  } catch (error) {
    console.error(`Error fetching page ${page} of document ${fileId}:`, error);
    throw error;
  }
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

// Export circuit breakers for status monitoring
export const getCircuitBreakerStatus = (key = 'default') => {
  return circuitBreakers[key]?.getStatus() || null;
};

export default api;
