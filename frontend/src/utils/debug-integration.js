// Debug helper module
// Add this to your project to help diagnose PDF loading issues

export const debugPDFLoading = (enabled = true) => {
    if (!enabled) return;
    
    // Original fetch to intercept and debug API calls
    const originalFetch = window.fetch;
    
    // Replace fetch with a wrapped version for debugging
    window.fetch = async (...args) => {
      const [resource, config] = args;
      
      // Check if this is a PDF page request
      if (typeof resource === 'string' && resource.includes('/pdfs/document/') && resource.includes('/page/')) {
        console.group(`🔍 Debug PDF Fetch: ${resource}`);
        console.log('Request config:', config);
        
        try {
          const response = await originalFetch(resource, config);
          
          // Clone the response so we can log it without consuming it
          const clonedResponse = response.clone();
          
          // Log response details
          console.log('Response status:', response.status);
          console.log('Response headers:', response.headers);
          
          try {
            // Try to get content length
            const contentLength = response.headers.get('content-length');
            console.log('Content length:', contentLength || 'Not provided');
            
            // Try to get content type
            const contentType = response.headers.get('content-type');
            console.log('Content type:', contentType || 'Not provided');
            
            // If it's an error response with JSON, log it
            if (!response.ok && contentType && contentType.includes('application/json')) {
              const errorData = await clonedResponse.json();
              console.error('Error response:', errorData);
            } else if (response.ok) {
              console.log('Response OK, will try to render PDF');
            }
          } catch (err) {
            console.error('Error analyzing response:', err);
          }
          
          console.groupEnd();
          return response;
        } catch (err) {
          console.error('Network error:', err);
          console.groupEnd();
          throw err;
        }
      }
      
      // For non-PDF requests, just use the original fetch
      return originalFetch(resource, config);
    };
    
    // Add listener for uncaught errors in the PDFRenderer
    window.addEventListener('error', (event) => {
      if (event.filename && event.filename.includes('PDFRenderer')) {
        console.error('PDF Renderer error:', event.message, event.error);
      }
    });
    
    console.log('📋 PDF debugging enabled - Monitoring PDF loading');
  };
  
  // Call this function in your index.js or App.js file
  // debugPDFLoading();