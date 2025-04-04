import React, { useState, useEffect, useRef } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { getDocumentPageDetails } from '../../services/api';
import Spinner from '../common/Spinner';
import PDFRenderer from './PDFRenderer';
import './EnhancedDiffView.css';

const EnhancedDiffView = ({ comparisonId, result, documentPair }) => {
  const { 
    state, 
    setSelectedPage, 
    setSelectedDifference 
  } = useComparison();
  
  const [pageDetails, setPageDetails] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [activeDifference, setActiveDifference] = useState(null);
  const [differences, setDifferences] = useState([]);
  const [retryCount, setRetryCount] = useState(0);
  const [maxRetries] = useState(3);
  
  const baseContainerRef = useRef(null);
  const compareContainerRef = useRef(null);
  
  // Calculate derived values
  let basePageCount = 0;
  let comparePageCount = 0;
  
  if (documentPair) {
    basePageCount = documentPair.basePageCount || 0;
    comparePageCount = documentPair.comparePageCount || 0;
  } else if (result) {
    basePageCount = result.basePageCount || 0;
    comparePageCount = result.comparePageCount || 0;
  }
  
  // Total pages is the maximum of base and compare
  const totalPages = Math.max(basePageCount, comparePageCount);
  
  // Ensure we don't try to access pages beyond the document pair's page count
  useEffect(() => {
    if (state.selectedPage > totalPages && totalPages > 0) {
      setSelectedPage(1);
    }
  }, [state.selectedPage, totalPages, setSelectedPage]);
  
  // Ensure we have a valid selected page
  useEffect(() => {
    if (!state.selectedPage || state.selectedPage < 1) {
      setSelectedPage(1);
    }
  }, [state.selectedPage, setSelectedPage]);
  
  // Fetch page details when selected page changes
  useEffect(() => {
    const fetchPageDetails = async () => {
      if (!comparisonId || !state.selectedPage) return;
      
      try {
        setLoading(true);
        setError(null);
        
        // Ensure we always use a valid page number (minimum 1)
        const pageNumber = Math.max(1, state.selectedPage);
        
        console.log(`Fetching comparison details for ID: ${comparisonId}, page: ${pageNumber}, pairIndex: ${state.selectedDocumentPairIndex}`);
        
        // Use document pair specific API for smart comparison mode
        const details = await getDocumentPageDetails(
          comparisonId,
          state.selectedDocumentPairIndex,
          pageNumber,
          state.filters
        );
        
        console.log('Page details received:', details);
        
        // Check if the page is out of bounds
        if (details && details.message && details.message.includes("Page not found")) {
          console.warn("Page not found in document pair:", details.message);
          
          // If we have a maxPage value, navigate to the last valid page
          if (details.maxPage && details.maxPage > 0) {
            console.log(`Navigating to max page: ${details.maxPage}`);
            setSelectedPage(details.maxPage);
            setLoading(false);
            return;
          }
          
          // Otherwise, show an error
          setError(`${details.message}. Please navigate to a valid page.`);
          setLoading(false);
          return;
        }
        
        setPageDetails(details);
        
        // Combine base and compare differences, removing duplicates
        const allDiffs = [];
        
        if (details.baseDifferences && details.baseDifferences.length > 0) {
          allDiffs.push(...details.baseDifferences.map(diff => ({
            ...diff,
            source: 'base'
          })));
        }
        
        if (details.compareDifferences && details.compareDifferences.length > 0) {
          // Only add differences not already included from base
          const uniqueCompareDiffs = details.compareDifferences.filter(
            compDiff => !details.baseDifferences.some(
              baseDiff => baseDiff.id === compDiff.id
            )
          );
          
          allDiffs.push(...uniqueCompareDiffs.map(diff => ({
            ...diff,
            source: 'compare'
          })));
        }
        
        // Group differences by type
        const groupedDiffs = allDiffs.reduce((acc, diff) => {
          const type = diff.type || 'unknown';
          if (!acc[type]) {
            acc[type] = [];
          }
          acc[type].push(diff);
          return acc;
        }, {});
        
        // Flatten grouped differences
        const flattenedDiffs = Object.entries(groupedDiffs).flatMap(([type, diffs]) => {
          return diffs.map((diff, index) => ({
            ...diff,
            groupIndex: index,
            groupCount: diffs.length
          }));
        });
        
        setDifferences(flattenedDiffs);
        setLoading(false);
        setRetryCount(0);
      } catch (err) {
        console.error('Error fetching page details:', err);
        
        // Check if this is a circuit breaker error
        if (err.circuitBreakerOpen || (err.message && err.message.includes("Service temporarily unavailable"))) {
          console.log('Circuit breaker is open, showing error message');
          setError('Service temporarily unavailable due to high load. Please try again in a few moments.');
          setLoading(false);
          
          // Try again after a longer delay
          setTimeout(() => {
            setRetryCount(prev => prev + 1);
          }, 10000); // 10 seconds
          return;
        }
        
        // Handle "still processing" error differently from other errors
        if (err.message && err.message.includes("still processing") && retryCount < maxRetries) {
          console.log(`Comparison still processing. Retry ${retryCount + 1}/${maxRetries} in 3 seconds...`);
          setError('Comparison details are still being processed. Please wait a moment...');
          setLoading(false);
          
          // Try again after a delay
          setTimeout(() => {
            setRetryCount(prev => prev + 1);
          }, 3000);
        } else {
          setError('Failed to load page comparison details. Please try navigating to another page.');
          setLoading(false);
        }
      }
    };
    
    fetchPageDetails();
  }, [comparisonId, state.selectedPage, state.filters, retryCount, maxRetries, state.selectedDocumentPairIndex, setSelectedPage]);

  // Handler for difference selection
  const handleDifferenceSelect = (difference) => {
    console.log("Difference selected:", difference);
    setSelectedDifference(difference);
    setActiveDifference(difference);
    
    // Scroll to the difference location if available
    if (difference.position && baseContainerRef.current && compareContainerRef.current) {
      const { x, y } = difference.position;
      
      // Calculate scroll position (center the difference in the viewport)
      const container = baseContainerRef.current;
      const containerWidth = container.clientWidth;
      const containerHeight = container.clientHeight;
      
      const scrollLeft = x - containerWidth / 2;
      const scrollTop = y - containerHeight / 2;
      
      // Apply scrolling
      baseContainerRef.current.scrollTo({
        left: scrollLeft > 0 ? scrollLeft : 0,
        top: scrollTop > 0 ? scrollTop : 0,
        behavior: 'smooth'
      });
      
      if (state.viewSettings?.syncScroll && compareContainerRef.current) {
        compareContainerRef.current.scrollTo({
          left: scrollLeft > 0 ? scrollLeft : 0,
          top: scrollTop > 0 ? scrollTop : 0,
          behavior: 'smooth'
        });
      }
    }
  };
  
  // Handler for synchronized scrolling
  const handleScroll = (event, source) => {
    if (!state.viewSettings?.syncScroll) return;
    
    const { scrollTop, scrollLeft } = event.target;
    
    if (source === 'base' && compareContainerRef.current) {
      compareContainerRef.current.scrollTop = scrollTop;
      compareContainerRef.current.scrollLeft = scrollLeft;
    } else if (source === 'compare' && baseContainerRef.current) {
      baseContainerRef.current.scrollTop = scrollTop;
      baseContainerRef.current.scrollLeft = scrollLeft;
    }
  };
  
  // Get icon for difference type
  const getTypeIcon = (type) => {
    switch (type) {
      case 'text':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M2.5 4v3h5v12h3V7h5V4h-13zm19 5h-9v3h3v7h3v-7h3V9z" />
          </svg>
        );
      case 'image':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" />
          </svg>
        );
      case 'font':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M9.93 13.5h4.14L12 7.98zM20 2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-4.05 16.5l-1.14-3H9.17l-1.12 3H5.96l5.11-13h1.86l5.11 13h-2.09z" />
          </svg>
        );
      case 'style':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M2.53 19.65l1.34.56v-9.03l-2.43 5.86c-.41 1.02.08 2.19 1.09 2.61zm19.5-3.7L17.07 3.98c-.31-.75-1.04-1.21-1.81-1.23-.26 0-.53.04-.79.15L7.1 5.95c-.75.31-1.21 1.03-1.23 1.8-.01.27.04.54.15.8l4.96 11.97c.31.76 1.05 1.22 1.83 1.23.26 0 .52-.05.77-.15l7.36-3.05c1.02-.42 1.51-1.59 1.09-2.6z" />
          </svg>
        );
      case 'metadata':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z" />
          </svg>
        );
      default:
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z" />
          </svg>
        );
    }
  };
  
  // Get change type indicator
  const getChangeTypeIndicator = (diff) => {
    if (diff.changeType === 'added') {
      return (
        <span className="change-type added">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" />
          </svg>
          Added
        </span>
      );
    } else if (diff.changeType === 'deleted') {
      return (
        <span className="change-type deleted">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M19 13H5v-2h14v2z" />
          </svg>
          Removed
        </span>
      );
    } else if (diff.changeType === 'modified') {
      return (
        <span className="change-type modified">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z" />
          </svg>
          Modified
        </span>
      );
    }
    
    return null;
  };
  
  // Get difference description
  const getDifferenceDescription = (diff) => {
    if (diff.description) {
      return diff.description;
    }
    
    switch (diff.type) {
      case 'text':
        return (
          <>
            <strong>Text:</strong> {diff.baseText || ''} → {diff.compareText || ''}
          </>
        );
      case 'image':
        return (
          <>
            <strong>Image:</strong> {diff.imageName || 'Unnamed image'}
          </>
        );
      case 'font':
        return (
          <>
            <strong>Font:</strong> {diff.fontName || 'Font difference'}
          </>
        );
      case 'style':
        return (
          <>
            <strong>Style:</strong> {diff.text ? `"${diff.text}"` : 'Style difference'}
          </>
        );
      default:
        return 'Difference detected';
    }
  };
  
  // Get difference details
  const getDifferenceDetails = (diff) => {
    switch (diff.type) {
      case 'text':
        return (
          <div className="difference-text-details">
            <div className="text-comparison">
              <div className="comparison-label">Base Text:</div>
              <div className="comparison-value base">{diff.baseText || '(empty)'}</div>
            </div>
            <div className="text-comparison">
              <div className="comparison-label">Compare Text:</div>
              <div className="comparison-value compare">{diff.compareText || '(empty)'}</div>
            </div>
          </div>
        );
      case 'image':
        return (
          <div className="difference-image-details">
            <div className="image-name">
              <div className="detail-label">Name:</div>
              <div className="detail-value">{diff.imageName || 'Unnamed image'}</div>
            </div>
            <div className="image-dimension">
              <div className="detail-label">Size:</div>
              <div className="detail-value">{diff.width || '?'}x{diff.height || '?'} pixels</div>
            </div>
            {diff.changes && (
              <div className="image-changes">
                <div className="detail-label">Changes:</div>
                <ul className="changes-list">
                  {diff.changes.map((change, index) => (
                    <li key={index}>{change}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        );
      case 'font':
        return (
          <div className="difference-font-details">
            <div className="font-name">
              <div className="detail-label">Name:</div>
              <div className="detail-value">{diff.fontName || 'Unknown font'}</div>
            </div>
            <div className="font-family">
              <div className="detail-label">Family:</div>
              <div className="detail-value">{diff.fontFamily || 'Unknown family'}</div>
            </div>
          </div>
        );
      default:
        return null;
    }
  };

  // If we're still loading result data
  if (!result) {
    return (
      <div className="enhanced-diff-view-loading">
        <Spinner size="large" />
        <p>Loading comparison results...</p>
      </div>
    );
  }

  // Loading state for page details
  if (loading && !pageDetails) {
    return (
      <div className="enhanced-diff-view-loading">
        <Spinner size="large" />
        <p>Loading page comparison...</p>
      </div>
    );
  }
  
  // Error state
  if (error) {
    return (
      <div className="enhanced-diff-view-error">
        <div className="error-icon">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
        </div>
        <h3>Error Loading Page Comparison</h3>
        <p>{error}</p>
        <button onClick={() => {
          setError(null);
          setRetryCount(0);
          setLoading(true);
        }}>Retry</button>
      </div>
    );
  }

  return (
    <div className="enhanced-diff-view">
      <div className="difference-sidebar">
        <div className="difference-sidebar-header">
          <h3>Differences</h3>
          <div className="difference-count">
            {differences.length} difference{differences.length !== 1 ? 's' : ''} found
          </div>
        </div>
        
        <div className="difference-list">
          {differences.length === 0 ? (
            <div className="no-differences">
              <p>No differences found on this page.</p>
            </div>
          ) : (
            differences.map((diff) => (
              <div 
                key={diff.id}
                className={`difference-item ${diff.type} ${activeDifference && activeDifference.id === diff.id ? 'active' : ''}`}
                onClick={() => handleDifferenceSelect(diff)}
              >
                <div className="difference-header">
                  <div className="difference-type">
                    <span className={`type-icon ${diff.type || 'unknown'}`}>
                      {getTypeIcon(diff.type || 'unknown')}
                    </span>
                  </div>
                  
                  {getChangeTypeIndicator(diff)}
                </div>
                
                <div className="difference-content">
                  <div className="difference-description">
                    {getDifferenceDescription(diff)}
                  </div>
                  
                  {activeDifference && activeDifference.id === diff.id && (
                    <div className="difference-details">
                      {getDifferenceDetails(diff)}
                    </div>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
      
      <div className="document-view">
        <div className="document-container base" ref={baseContainerRef} onScroll={(e) => handleScroll(e, 'base')}>
          {documentPair && (
            <div className="document-image-container">
              <PDFRenderer 
                fileId={result.baseFileId}
                page={documentPair.baseStartPage + (state.selectedPage - 1)}
                zoom={state.viewSettings?.zoom || 1}
                highlightMode={state.viewSettings?.highlightMode || 'all'}
                differences={pageDetails?.baseDifferences || []}
                selectedDifference={state.selectedDifference}
                onDifferenceSelect={handleDifferenceSelect}
                loading={loading}
                interactive={true}
              />
            </div>
          )}
        </div>
        
        <div className="document-container compare" ref={compareContainerRef} onScroll={(e) => handleScroll(e, 'compare')}>
          {documentPair && (
            <div className="document-image-container">
              <PDFRenderer 
                fileId={result.compareFileId}
                page={documentPair.compareStartPage + (state.selectedPage - 1)}
                zoom={state.viewSettings?.zoom || 1}
                highlightMode={state.viewSettings?.highlightMode || 'all'}
                differences={pageDetails?.compareDifferences || []}
                selectedDifference={state.selectedDifference}
                onDifferenceSelect={handleDifferenceSelect}
                loading={loading}
                interactive={true}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default EnhancedDiffView;
