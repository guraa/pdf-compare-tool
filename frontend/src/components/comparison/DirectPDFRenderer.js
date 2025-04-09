import React, { useState, useEffect, useRef } from 'react';
import Spinner from '../common/Spinner';
import './DirectPDFRenderer.css';

/**
 * A component for directly rendering PDF pages as images
 * with support for highlighting differences
 */
const DirectPDFRenderer = ({ fileId, page, differences = [], onSelectDifference, selectedDifference }) => {
  // Component state
  const [imageUrl, setImageUrl] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [loadAttempt, setLoadAttempt] = useState(0);
  
  // Track if component is mounted
  const mounted = useRef(true);
  const imgRef = useRef(null);
  
  console.log(`DirectPDFRenderer rendering: fileId=${fileId}, page=${page}, loadAttempt=${loadAttempt}`);
  
  // Load PDF image directly with fetch
  useEffect(() => {
    const loadPdfImage = async () => {
      if (!fileId || !page) {
        console.log('Missing fileId or page, cannot load PDF');
        return;
      }
      
      try {
        setLoading(true);
        setError(null);
        
        console.log(`Loading PDF image (attempt ${loadAttempt+1}): fileId=${fileId}, page=${page}`);
        
        // Direct fetch approach instead of using the API function
        const response = await fetch(`/api/pdfs/document/${fileId}/page/${page}`, {
          headers: {
            'Accept': 'image/png',  // Explicitly request image format
            'Cache-Control': 'no-cache'  // Avoid caching issues
          }
        });
        
        if (!mounted.current) {
          console.log('Component unmounted during fetch, aborting');
          return;
        }
        
        if (!response.ok) {
          throw new Error(`Failed to fetch PDF image: ${response.status} ${response.statusText}`);
        }
        
        const contentType = response.headers.get('content-type');
        console.log(`Response content type: ${contentType}`);
        
        const blob = await response.blob();
        console.log(`Blob received: size=${blob.size}, type=${blob.type}`);
        
        // Create object URL
        const url = URL.createObjectURL(blob);
        console.log(`Image URL created: ${url}`);
        
        setImageUrl(url);
        setLoading(false);
      } catch (err) {
        if (!mounted.current) return;
        
        console.error(`Error loading PDF image: ${err.message}`, err);
        setError(`Failed to load image: ${err.message}`);
        setLoading(false);
        
        // Try again after a short delay (max 3 attempts)
        if (loadAttempt < 2) {
          console.log(`Scheduling retry attempt ${loadAttempt + 2}...`);
          setTimeout(() => {
            if (mounted.current) {
              setLoadAttempt(prev => prev + 1);
            }
          }, 1500);
        }
      }
    };
    
    loadPdfImage();
  }, [fileId, page, loadAttempt]);
  
  // Clean up on unmount
  useEffect(() => {
    return () => {
      console.log('DirectPDFRenderer unmounting, cleaning up resources');
      mounted.current = false;
      if (imageUrl) {
        URL.revokeObjectURL(imageUrl);
      }
    };
  }, [imageUrl]);
  
  // Handle image load success
  const handleImageLoad = () => {
    console.log('Image loaded successfully!', { width: imgRef.current?.naturalWidth, height: imgRef.current?.naturalHeight });
  };
  
  // Handle image load error
  const handleImageError = (e) => {
    console.error('Image failed to load', e);
    setError('Image failed to load. Please try again.');
    
    // Try again if not too many attempts
    if (loadAttempt < 2) {
      setTimeout(() => {
        if (mounted.current) {
          setLoadAttempt(prev => prev + 1);
        }
      }, 1500);
    }
  };
  
  if (loading) {
    return (
      <div className="pdf-loading">
        <Spinner size="medium" />
        <p>Loading document{loadAttempt > 0 ? ` (attempt ${loadAttempt+1})` : ''}...</p>
      </div>
    );
  }
  
  if (error) {
    return (
      <div className="pdf-error">
        <p>{error}</p>
        {loadAttempt < 2 && (
          <button onClick={() => setLoadAttempt(prev => prev + 1)}>
            Retry
          </button>
        )}
      </div>
    );
  }
  
  if (!imageUrl) {
    return (
      <div className="pdf-empty">
        <p>No document available</p>
        <button onClick={() => setLoadAttempt(prev => prev + 1)}>
          Retry
        </button>
      </div>
    );
  }
  
  return (
    <div className="pdf-container">
      <div className="pdf-debug-info">
        FileID: {fileId}<br />
        Page: {page}<br />
        URL: {imageUrl ? '✓' : '✗'}<br />
        Attempt: {loadAttempt + 1}
      </div>
      
      <div className="pdf-image-wrapper">
        <img 
          ref={imgRef}
          src={imageUrl} 
          alt="PDF page" 
          className="pdf-image"
          onLoad={handleImageLoad}
          onError={handleImageError}
        />
        
        <div className="highlight-overlay">
          {differences.map(diff => (
            <div 
              key={diff.id}
              className={`highlight ${diff.type || ''} ${diff.changeType || ''} ${selectedDifference?.id === diff.id ? 'selected' : ''}`}
              style={{
                left: diff.position?.x || 0,
                top: diff.position?.y || 0,
                width: diff.bounds?.width || 100,
                height: diff.bounds?.height || 30
              }}
              onClick={() => onSelectDifference(diff)}
              title={diff.description || `${diff.type} ${diff.changeType}`}
            />
          ))}
        </div>
      </div>
    </div>
  );
};

export default DirectPDFRenderer;s