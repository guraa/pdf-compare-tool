import axios from 'axios';

// Circuit breaker state
const circuitBreaker = {
  failures: 0,
  lastFailureTime: 0,
  isOpen: false,
  threshold: 5, // Number of failures before opening the circuit
  resetTimeout: 30000, // 30 seconds before trying again
};

// Create an axios instance with default config
const api = axios.create({
  baseURL: '/api', // Base URL for all API requests
  headers: {
    'Content-Type': 'application/json'
  },
  // Add timeout to prevent indefinite waiting
  timeout: 120000 // 30 seconds
});

// Add request interceptor for error handling and circuit breaker
api.interceptors.request.use(
  config => {
    // Check if circuit breaker is open
    if (circuitBreaker.isOpen) {
      const now = Date.now();
      const timeSinceLastFailure = now - circuitBreaker.lastFailureTime;
      
      // If enough time has passed, allow one request through to test if the service is back
      if (timeSinceLastFailure > circuitBreaker.resetTimeout) {
        console.log('Circuit breaker: Testing if service is back...');
        circuitBreaker.isOpen = false;
      } else {
        // Circuit is still open, reject the request
        console.log(`Circuit breaker: Open (${Math.round(timeSinceLastFailure / 1000)}s since last failure, will retry after ${Math.round(circuitBreaker.resetTimeout / 1000)}s)`);
        return Promise.reject(new Error('Circuit breaker is open. Too many failed requests.'));
      }
    }
    
    // Log request for debugging
    console.log(`API Request: ${config.method.toUpperCase()} ${config.url}`, config);
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
    // Log successful responses for debugging
    console.log('API Response:', response.status, response.statusText);
    
    // Reset circuit breaker on successful response
    if (circuitBreaker.failures > 0) {
      console.log('Circuit breaker: Resetting after successful response');
      circuitBreaker.failures = 0;
      circuitBreaker.isOpen = false;
    }
    
    return response;
  },
  error => {
    // Handle API errors gracefully
    console.error('API Error:', error);
    
    // Update circuit breaker state for network errors
    if (error.code === 'ERR_NETWORK' || error.code === 'ECONNABORTED' || error.message.includes('timeout')) {
      circuitBreaker.failures++;
      circuitBreaker.lastFailureTime = Date.now();
      
      console.log(`Circuit breaker: Failure count increased to ${circuitBreaker.failures}/${circuitBreaker.threshold}`);
      
      // Open the circuit if we've hit the threshold
      if (circuitBreaker.failures >= circuitBreaker.threshold) {
        console.log('Circuit breaker: Opening circuit due to too many failures');
        circuitBreaker.isOpen = true;
      }
    }
    
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

export const compareDocuments = async (baseFileId, compareFileId, options = {}) => {
    // Always enable smart matching regardless of input options
    const requestOptions = { 
      ...options,
      smartMatching: true  // Force smart matching to be enabled
    };
    
    console.log('Sending comparison request with options:', requestOptions);
  
    try {
      const response = await api.post('/pdfs/compare', {
        baseFileId,
        compareFileId,
        options: requestOptions
      });
      
      console.log('Comparison API response:', response.data);
      return response.data;
    } catch (error) {
      console.error('Error in compareDocuments API call:', error);
      
      // Provide more detailed error information if available
      if (error.response && error.response.data) {
        console.error('API error details:', error.response.data);
      }
      
      throw error;
    }
  };

export const getComparisonResult = async (comparisonId) => {
  try {
    // Use a longer timeout for this specific request as it might take time
    const response = await api.get(`/pdfs/comparison/${comparisonId}`, {
      timeout: 120000, // 60 seconds timeout for comparison results
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
    console.error("Error fetching comparison result:", error);
    throw error;
  }
};

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
      console.log(`Making API request to: /pdfs/comparison/${comparisonId}/page/${pageNumber} with types: ${params.types}`, params);
      
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
        
        // Debug log the response structure
        console.log('Page details response structure:', Object.keys(response.data));
        
        // Handle empty response
        if (!response.data) {
          console.error("Empty response from server");
          throw new Error("Empty response from server");
        }
        
        // Ensure differences arrays exist
        if (!response.data.baseDifferences) {
          response.data.baseDifferences = [];
          console.warn("baseDifferences missing, initialized to empty array");
        }
        
        if (!response.data.compareDifferences) {
          response.data.compareDifferences = [];
          console.warn("compareDifferences missing, initialized to empty array");
        }
        
        // Log difference counts
        console.log(`Found ${response.data.baseDifferences.length} base differences and ${response.data.compareDifferences.length} compare differences`);
        
        // Add debugging sample
        if (response.data.baseDifferences.length > 0) {
          console.log('Sample base difference:', response.data.baseDifferences[0]);
        }
        
        if (response.data.compareDifferences.length > 0) {
          console.log('Sample compare difference:', response.data.compareDifferences[0]);
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

export const getDocumentPairs = async (comparisonId) => {
    try {
      // Use a longer timeout for this specific request as it might take time
      const response = await api.get(`/pdfs/comparison/${comparisonId}/documents`, {
        timeout: 120000, // 60 seconds timeout
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
      console.error("Error fetching document pairs:", error);
      
      // Handle circuit breaker errors
      if (error.message === 'Circuit breaker is open. Too many failed requests.') {
        // Return a special error that the UI can handle
        throw new Error("Service temporarily unavailable due to too many failed requests. Please try again later.");
      }
      
      throw error;
    }
  };
  
  // Get comparison result for a specific document pair
  export const getDocumentPairResult = async (comparisonId, pairIndex) => {
    try {
      const response = await api.get(`/pdfs/comparison/${comparisonId}/documents/${pairIndex}`, {
        timeout: 120000, // 60 seconds timeout
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
      if (error.message === 'Circuit breaker is open. Too many failed requests.') {
        // Return a special error that the UI can handle
        throw new Error("Service temporarily unavailable due to too many failed requests. Please try again later.");
      }
      
      throw error;
    }
  };
  
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
    
    // Log debug info
    console.log(`Getting document page details for comparison: ${comparisonId}, pair: ${pairIndex}, page: ${pageNumber} with types: ${params.types}`);
    
    try {
      // Check if the page number is valid (not greater than the document's page count)
      // This is a client-side validation to prevent unnecessary 404 errors
      const response = await api.get(
        `/pdfs/comparison/${comparisonId}/documents/${pairIndex}/page/${pageNumber}`, 
        { 
          params,
          // Add validateStatus to accept 404 responses
          validateStatus: function (status) {
            return status === 200 || status === 404 || status === 202;
          }
        }
      );
      
      // If we got a 404 response, return empty data with a message
      if (response.status === 404) {
        console.log("Endpoint returned 404, returning empty data structure with page not found message");
        
        // Check if the response contains information about the max page
        let message = "Page not found in document pair";
        let maxPage = null;
        
        if (response.data && response.data.error) {
          message = response.data.error;
          
          if (response.data.maxPage) {
            maxPage = response.data.maxPage;
          }
        }
        
        return {
          baseDifferences: [],
          compareDifferences: [],
          message: message,
          maxPage: maxPage
        };
      }
      
      // If status is 202, the comparison is still processing
      if (response.status === 202) {
        console.log("Comparison still processing");
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
      console.error(`Error fetching page details for page ${pageNumber} of document pair ${pairIndex}:`, error);
      
      // Handle circuit breaker errors
      if (error.message === 'Circuit breaker is open. Too many failed requests.') {
        return {
          baseDifferences: [],
          compareDifferences: [],
          message: "Service temporarily unavailable due to high load. Please try again later.",
          error: error,
          circuitBreakerOpen: true
        };
      }
      
      // If the original request fails with 404, return empty data with a message
      if (error.response && error.response.status === 404) {
        console.log("Endpoint returned 404, returning empty data structure with page not found message");
        
        // Check if the response contains information about the max page
        let message = "Page not found in document pair";
        let maxPage = null;
        
        if (error.response.data && error.response.data.error) {
          message = error.response.data.error;
          
          if (error.response.data.maxPage) {
            maxPage = error.response.data.maxPage;
          }
        }
        
        return {
          baseDifferences: [],
          compareDifferences: [],
          message: message,
          maxPage: maxPage
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

export default api;
