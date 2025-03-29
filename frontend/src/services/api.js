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
      timeout: 60000, // 60 seconds timeout for comparison results
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
    try {
      // Convert filter objects to query parameters
      const params = {};
      
      if (filters && filters.differenceTypes && filters.differenceTypes.length > 0) {
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
      
      // Debug logging to check URL and parameters
      console.log(`Making API request to: /pdfs/comparison/${comparisonId}/page/${pageNumber}`);
      console.log('Query parameters:', params);
      
      try {
        const response = await api.get(`/pdfs/comparison/${comparisonId}/page/${pageNumber}`, { 
          params,
          // Set longer timeout for potentially large responses
          timeout: 60000,
          validateStatus: function (status) {
            // Accept 202 Accepted as a valid status for "still processing"
            return (status >= 200 && status < 300) || status === 202;
          }
        });
        
        // Check if the response indicates the comparison is still processing
        if (response.status === 202) {
          throw new Error("Comparison still processing");
        }
        
        // Debug log the actual response structure
        console.log('Page details response:', response.data);
        
        // Handle empty or malformed response
        if (!response.data) {
          console.error("Empty response from server");
          throw new Error("Empty response from server");
        }
        
        // Check for differences data structure
        if (!response.data.baseDifferences && !response.data.compareDifferences) {
          console.warn("No differences arrays found in response", response.data);
        }
        
        // Initialize differences arrays if missing
        if (!response.data.baseDifferences) {
          response.data.baseDifferences = [];
          console.warn("baseDifferences missing, initialized to empty array");
        }
        
        if (!response.data.compareDifferences) {
          response.data.compareDifferences = [];
          console.warn("compareDifferences missing, initialized to empty array");
        }
        
        // Check structure of response data
        if (response.data.baseDifferences && response.data.baseDifferences.length > 0) {
          console.log(`Found ${response.data.baseDifferences.length} base differences`);
          console.log('First base difference sample:', response.data.baseDifferences[0]);
        }
        
        if (response.data.compareDifferences && response.data.compareDifferences.length > 0) {
          console.log(`Found ${response.data.compareDifferences.length} compare differences`);
          console.log('First compare difference sample:', response.data.compareDifferences[0]);
        }
        
        return response.data;
      } catch (error) {
        // Check if error is a network error
        if (error.message && error.message.includes('Network Error')) {
          console.error('Network error when fetching page details:', error);
          throw new Error("Network error when connecting to the server. Please check your connection.");
        }
        
        // Handle 404 specifically
        if (error.response && error.response.status === 404) {
          console.error(`Page ${pageNumber} not found in comparison ${comparisonId}:`, error);
          
          // Create a minimal valid response to prevent UI from crashing
          return {
            baseDifferences: [],
            compareDifferences: [],
            pageNumber: pageNumber,
            error: `Page ${pageNumber} not found or no differences detected`
          };
        }
        
        // Re-throw other errors
        throw error;
      }
    } catch (error) {
      console.error(`Error fetching page ${page} details for comparison ${comparisonId}:`, error);
      
      if (error.message === "Comparison still processing") {
        // Propagate processing status
        throw error;
      }
      
      // Try to use a fallback to get at least some data
      try {
        console.log("Attempting fallback request to get general comparison results...");
        const response = await api.get(`/pdfs/comparison/${comparisonId}`);
        
        // Log the general comparison result
        console.log("Fallback comparison results:", response.data);
        
        // Return minimal page details structure
        return {
          baseDifferences: [],
          compareDifferences: [],
          pageNumber: parseInt(page) || 1,
          fetchError: error.message || "Failed to fetch detailed page information",
          basePageCount: response.data.basePageCount || 0,
          comparePageCount: response.data.comparePageCount || 0
        };
      } catch (fallbackError) {
        console.error("Fallback request also failed:", fallbackError);
        // Return minimal data structure to prevent UI crashes
        return {
          baseDifferences: [],
          compareDifferences: [],
          pageNumber: parseInt(page) || 1,
          fetchError: error.message || "Failed to fetch comparison details"
        };
      }
    }
  };

export const getDocumentPairs = async (comparisonId) => {
    try {
      // Use a longer timeout for this specific request as it might take time
      const response = await api.get(`/pdfs/comparison/${comparisonId}/documents`, {
        timeout: 60000, // 60 seconds timeout
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
      throw error;
    }
  };
  
  // Get comparison result for a specific document pair
  export const getDocumentPairResult = async (comparisonId, pairIndex) => {
    try {
      const response = await api.get(`/pdfs/comparison/${comparisonId}/documents/${pairIndex}`, {
        timeout: 60000, // 60 seconds timeout
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
      throw error;
    }
  };
  
  // Get page details for a specific page in a document pair
  export const getDocumentPageDetails = async (comparisonId, pairIndex, pageNumber, filters = {}) => {
    // Convert filter objects to simple string parameters
    const params = {};
    
    // Handle filter parameters properly
    if (filters.differenceTypes && filters.differenceTypes.length > 0) {
      params.types = filters.differenceTypes.join(',');
    }
    
    if (filters.minSeverity) {
      params.severity = filters.minSeverity;
    }
    
    if (filters.searchTerm) {
      params.search = filters.searchTerm;
    }
    
    try {
      const response = await api.get(
        `/pdfs/comparison/${comparisonId}/documents/${pairIndex}/page/${pageNumber}`, 
        { 
          params,
          validateStatus: function (status) {
            // Accept 202 Accepted as a valid response (still processing)
            return (status >= 200 && status < 300) || status === 202;
          }
        }
      );
      
      // If status is 202, throw a "still processing" error that the retry logic can handle
      if (response.status === 202) {
        throw new Error("Comparison still processing");
      }
      
      return response.data;
    } catch (error) {
      console.error(`Error fetching page details for page ${pageNumber} of document pair ${pairIndex}:`, error);
      throw error;
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