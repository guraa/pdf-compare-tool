import React, { useState, useEffect } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { getDocumentPairs } from '../../services/api';
import Spinner from '../common/Spinner';
import './DocumentMatchingView.css';

const DocumentMatchingView = ({ comparisonId, onSelectDocumentPair }) => {
  const [documentPairs, setDocumentPairs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedPairIndex, setSelectedPairIndex] = useState(null);
  const [retryCount, setRetryCount] = useState(0);
  const [maxRetries] = useState(15); // More retries for document matching

  // Fetch document pairs when component mounts
  useEffect(() => {
    const fetchDocumentPairs = async () => {
      if (!comparisonId) return;
      
      try {
        setLoading(true);
        const pairs = await getDocumentPairs(comparisonId);
        
        if (pairs && pairs.length > 0) {
          setDocumentPairs(pairs);
          setLoading(false);
          
          // Auto-select the first matched pair if available
          const matchedPairIndex = pairs.findIndex(pair => pair.matched);
          if (matchedPairIndex >= 0) {
            setSelectedPairIndex(matchedPairIndex);
          } else {
            setSelectedPairIndex(0);
          }
        } else {
          throw new Error("No document pairs returned");
        }
      } catch (err) {
        console.error('Error fetching document pairs:', err);
        
        // Check if this is a circuit breaker error
        if (err.message.includes("Service temporarily unavailable") || err.message.includes("Circuit breaker is open")) {
          setError('Service temporarily unavailable due to high load. Please try again later.');
          setLoading(false);
          
          // Set a longer retry delay for circuit breaker errors
          setTimeout(() => {
            setRetryCount(prev => prev + 1);
          }, 120000); // 30 seconds
          
          return;
        }
        
        // Check if we need to retry (could be still processing)
        if ((err.message.includes("still processing") || err.response?.status === 202) && retryCount < maxRetries) {
          setLoading(false);
          
          // Wait longer between retries as the count increases
          const delay = Math.min(2000 * Math.pow(1.5, retryCount), 15000);
          
          console.log(`Retrying in ${delay}ms (attempt ${retryCount + 1}/${maxRetries})...`);
          
          setTimeout(() => {
            setRetryCount(prev => prev + 1);
          }, delay);
        } else if (err.code === 'ERR_NETWORK' && retryCount < maxRetries) {
          // Handle network errors specifically
          setLoading(false);
          
          // Use a longer delay for network errors to give the server time to recover
          const delay = Math.min(5000 * Math.pow(1.5, retryCount), 30000);
          
          console.log(`Network error. Retrying in ${delay}ms (attempt ${retryCount + 1}/${maxRetries})...`);
          
          setTimeout(() => {
            setRetryCount(prev => prev + 1);
          }, delay);
        } else if (retryCount >= maxRetries) {
          setError('Failed to load document pairs after multiple attempts. Please try refreshing the page.');
          setLoading(false);
        } else {
          setError('Failed to load document pairs. The document matching may have failed.');
          setLoading(false);
        }
      }
    };
    
    fetchDocumentPairs();
  }, [comparisonId, retryCount, maxRetries]);
  
  // Handle selecting a document pair
  const handleSelectPair = (index) => {
    setSelectedPairIndex(index);
    
    if (onSelectDocumentPair) {
      onSelectDocumentPair(index, documentPairs[index]);
    }
  };
  
  // Calculate similarity display text
  const getSimilarityText = (score) => {
    if (score >= 0.95) return 'Excellent Match';
    if (score >= 0.85) return 'Good Match';
    if (score >= 0.70) return 'Fair Match';
    if (score >= 0.50) return 'Possible Match';
    if (score > 0) return 'Poor Match';
    return 'No Match';
  };
  
  // Get color based on similarity score
  const getSimilarityColor = (score) => {
    if (score >= 0.95) return '#4CAF50'; // Green
    if (score >= 0.85) return '#8BC34A'; // Light Green
    if (score >= 0.70) return '#CDDC39'; // Lime
    if (score >= 0.50) return '#FFC107'; // Amber
    if (score > 0) return '#FF9800';     // Orange
    return '#F44336';                    // Red
  };

  if (loading && documentPairs.length === 0) {
    return (
      <div className="document-matching-loading">
        <Spinner size="large" />
        <p>Analyzing documents and finding matches...</p>
        <p className="processing-info">This may take a few minutes for large documents.</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="document-matching-error">
        <div className="error-icon">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
        </div>
        <h3>Error Finding Document Matches</h3>
        <p>{error}</p>
        <button 
          className="retry-button" 
          onClick={() => {
            setError(null);
            setRetryCount(0);
          }}
        >
          Try Again
        </button>
      </div>
    );
  }

  if (documentPairs.length === 0 && retryCount < maxRetries) {
    return (
      <div className="document-matching-loading">
        <Spinner size="medium" />
        <p>Document matching in progress... (Attempt {retryCount + 1}/{maxRetries})</p>
        <p className="processing-info">This can take several minutes for large documents with many pages.</p>
      </div>
    );
  }
  
  // If we've hit max retries but still no document pairs
  if (documentPairs.length === 0) {
    return (
      <div className="document-matching-error">
        <div className="error-icon">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
        </div>
        <h3>Document Matching Failed</h3>
        <p>Unable to analyze and match documents after multiple attempts.</p>
        <button 
          className="retry-button" 
          onClick={() => {
            setError(null);
            setRetryCount(0);
          }}
        >
          Try Again
        </button>
      </div>
    );
  }

  return (
    <div className="document-matching-view">
      <div className="matching-header">
        <h3>Document Matching Results</h3>
        <p className="matching-summary">
          Found {documentPairs.length} document{documentPairs.length !== 1 ? 's' : ''} across your PDFs
        </p>
      </div>
      
      <div className="document-pairs-container">
        <div className="document-pairs-list">
          {documentPairs.map((pair, index) => (
            <div 
              key={index}
              className={`document-pair-item ${selectedPairIndex === index ? 'selected' : ''} ${pair.matched ? 'matched' : 'unmatched'}`}
              onClick={() => handleSelectPair(index)}
            >
              <div className="pair-header">
                <div className="pair-icon">
                  {pair.matched ? (
                    <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                      <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-3.71 10.29L12 16.17l-3.29-3.29a.996.996 0 1 1 1.41-1.41L12 13.17l2.59-2.59a.996.996 0 1 1 1.41 1.41z" />
                    </svg>
                  ) : pair.hasBaseDocument ? (
                    <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                      <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zM6 20V4h7v5h5v11H6z" />
                    </svg>
                  ) : (
                    <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                      <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zM6 20V4h7v5h5v11H6z" />
                    </svg>
                  )}
                </div>
                <div className="pair-title">
                  <h4>Document {index + 1}</h4>
                  {pair.matched && (
                    <div 
                      className="similarity-badge" 
                      style={{ backgroundColor: getSimilarityColor(pair.similarityScore) }}
                    >
                      {Math.round(pair.similarityScore * 100)}%
                    </div>
                  )}
                </div>
              </div>
              
              <div className="pair-content">
                {pair.matched ? (
                  <div className="matching-info">
                    <div className="match-quality">
                      {getSimilarityText(pair.similarityScore)}
                    </div>
                    <div className="page-info">
                      <div className="base-pages">
                        Base: Pages {pair.baseStartPage}-{pair.baseEndPage} ({pair.basePageCount} pages)
                      </div>
                      <div className="compare-pages">
                        Compare: Pages {pair.compareStartPage}-{pair.compareEndPage} ({pair.comparePageCount} pages)
                      </div>
                    </div>
                  </div>
                ) : pair.hasBaseDocument ? (
                  <div className="unmatched-info">
                    <div className="unmatch-label">Only in Base Document</div>
                    <div className="page-info">
                      Pages {pair.baseStartPage}-{pair.baseEndPage} ({pair.basePageCount} pages)
                    </div>
                  </div>
                ) : (
                  <div className="unmatched-info">
                    <div className="unmatch-label">Only in Compare Document</div>
                    <div className="page-info">
                      Pages {pair.compareStartPage}-{pair.compareEndPage} ({pair.comparePageCount} pages)
                    </div>
                  </div>
                )}
                
                {pair.totalDifferences !== undefined && (
                  <div className="difference-count">
                    {pair.totalDifferences} difference{pair.totalDifferences !== 1 ? 's' : ''}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
        
        <div className="selected-pair-details">
          {selectedPairIndex !== null && documentPairs[selectedPairIndex] && (
            <div className="pair-details">
              <h4>Document {selectedPairIndex + 1} Details</h4>
              
              <div className="pair-summary">
                {documentPairs[selectedPairIndex].matched ? (
                  <>
                    <div className="match-status matched">
                      Documents Matched ({getSimilarityText(documentPairs[selectedPairIndex].similarityScore)})
                    </div>
                    <div className="page-ranges">
                      <div className="page-range base">
                        <strong>Base Document:</strong> Pages {documentPairs[selectedPairIndex].baseStartPage}-
                        {documentPairs[selectedPairIndex].baseEndPage}
                      </div>
                      <div className="page-range compare">
                        <strong>Compare Document:</strong> Pages {documentPairs[selectedPairIndex].compareStartPage}-
                        {documentPairs[selectedPairIndex].compareEndPage}
                      </div>
                    </div>
                  </>
                ) : documentPairs[selectedPairIndex].hasBaseDocument ? (
                  <>
                    <div className="match-status unmatched">
                      Document Only in Base PDF
                    </div>
                    <div className="page-ranges">
                      <div className="page-range base">
                        <strong>Base Document:</strong> Pages {documentPairs[selectedPairIndex].baseStartPage}-
                        {documentPairs[selectedPairIndex].baseEndPage}
                      </div>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="match-status unmatched">
                      Document Only in Compare PDF
                    </div>
                    <div className="page-ranges">
                      <div className="page-range compare">
                        <strong>Compare Document:</strong> Pages {documentPairs[selectedPairIndex].compareStartPage}-
                        {documentPairs[selectedPairIndex].compareEndPage}
                      </div>
                    </div>
                  </>
                )}
              </div>
              
              {documentPairs[selectedPairIndex].matched && (
                <div className="view-options">
                  <button 
                    className="view-pair-button"
                    onClick={() => onSelectDocumentPair(selectedPairIndex, documentPairs[selectedPairIndex])}
                  >
                    View Comparison
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default DocumentMatchingView;
