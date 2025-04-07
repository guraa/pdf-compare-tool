import React, { useState, useEffect, useRef } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import SideBySideView from './SideBySideView';
import Spinner from '../common/Spinner';
import { getDocumentPairs, getDocumentPairResult } from '../../services/api';
import './SmartComparisonContainer.css';

const SmartComparisonContainer = ({ comparisonId }) => {
  // Reference to track if component is mounted
  const isMounted = useRef(true);
  const fetchInProgress = useRef(false);
  const pairsAlreadyFetched = useRef(false);
  
  const { 
    state, 
    setComparisonResult,
    setError,
    setLoading,
    setSelectedPage,
    setDocumentPairs,
    setSelectedDocumentPairIndex
  } = useComparison();
  
  const [activeView, setActiveView] = useState('matching'); // 'matching' or 'comparison'
  const [selectedPairIndex, setSelectedPairIndex] = useState(null);
  const [selectedPair, setSelectedPair] = useState(null);
  const [pairResult, setPairResult] = useState(null);
  const [loadingPair, setLoadingPair] = useState(false);
  const [pairError, setPairError] = useState(null);
  const [retryCount, setRetryCount] = useState(0);
  const [maxRetries] = useState(5);
  const [localDocumentPairs, setLocalDocumentPairs] = useState([]);
  
  // Set isMounted to false when component unmounts
  useEffect(() => {
    return () => {
      isMounted.current = false;
    };
  }, []);
  
  // Fetch document pairs when component mounts
  useEffect(() => {
    const fetchDocumentPairs = async () => {
      if (!comparisonId || !isMounted.current || fetchInProgress.current || pairsAlreadyFetched.current) {
        return;
      }
      
      try {
        fetchInProgress.current = true;
        setLoading(true);
        
        console.log(`Fetching document pairs for comparison: ${comparisonId} (attempt ${retryCount + 1}/${maxRetries})`);
        const pairs = await getDocumentPairs(comparisonId);
        
        // Check if component is still mounted
        if (!isMounted.current) return;
        
        if (pairs && pairs.length > 0) {
          console.log(`Received ${pairs.length} document pairs`);
          
          // Update state
          setLocalDocumentPairs(pairs);
          setDocumentPairs(pairs);
          
          // Set flag to prevent refetching
          pairsAlreadyFetched.current = true;
          
          // Auto-select the first matched pair if available
          const matchedPairIndex = pairs.findIndex(pair => pair.matched);
          if (matchedPairIndex >= 0) {
            setSelectedPairIndex(matchedPairIndex);
            setSelectedDocumentPairIndex(matchedPairIndex);
          } else {
            setSelectedPairIndex(0);
            setSelectedDocumentPairIndex(0);
          }
          
          setLoading(false);
        } else {
          throw new Error("No document pairs returned");
        }
      } catch (err) {
        // Check if component is still mounted
        if (!isMounted.current) return;
        
        console.error('Error fetching document pairs:', err);
        
        // Check if this is a circuit breaker error
        if (err.message.includes("Service temporarily unavailable") || err.message.includes("Circuit breaker is open")) {
          setError('Service temporarily unavailable due to high load. Please try again later.');
          setLoading(false);
          
          // Set a longer retry delay for circuit breaker errors
          setTimeout(() => {
            if (isMounted.current && retryCount < maxRetries) {
              setRetryCount(prev => prev + 1);
            }
          }, 30000); // 30 seconds
          
          return;
        }
        
        // Check if we need to retry (could be still processing)
        if ((err.message.includes("still processing") || err.response?.status === 202) && retryCount < maxRetries) {
          // Wait longer between retries as the count increases
          const delay = Math.min(2000 * Math.pow(1.5, retryCount), 15000);
          
          console.log(`Retrying in ${Math.round(delay/1000)}s (attempt ${retryCount + 1}/${maxRetries})...`);
          
          setTimeout(() => {
            if (isMounted.current) {
              setRetryCount(prev => prev + 1);
            }
          }, delay);
        } else if (err.code === 'ERR_NETWORK' && retryCount < maxRetries) {
          // Handle network errors specifically
          // Use a longer delay for network errors to give the server time to recover
          const delay = Math.min(5000 * Math.pow(1.5, retryCount), 30000);
          
          console.log(`Network error. Retrying in ${Math.round(delay/1000)}s (attempt ${retryCount + 1}/${maxRetries})...`);
          
          setTimeout(() => {
            if (isMounted.current) {
              setRetryCount(prev => prev + 1);
            }
          }, delay);
        } else if (retryCount >= maxRetries) {
          setError('Failed to load document pairs after multiple attempts. Please try refreshing the page.');
          setLoading(false);
        } else {
          setError('Failed to load document pairs. The document matching may have failed.');
          setLoading(false);
        }
      } finally {
        fetchInProgress.current = false;
      }
    };
    
    fetchDocumentPairs();
  }, [comparisonId, setDocumentPairs, setLoading, setError, setSelectedDocumentPairIndex, retryCount, maxRetries]);
  
  // Handle selecting a document pair for comparison
  const handleSelectDocumentPair = async (pairIndex, pair) => {
    setSelectedPairIndex(pairIndex);
    setSelectedPair(pair);
    
    // Only load comparison result if the pair is matched
    if (pair.matched) {
      try {
        setLoadingPair(true);
        setPairError(null);
        
        console.log(`Loading comparison result for document pair ${pairIndex}`);
        
        // Set a timeout to prevent infinite loading
        const timeoutPromise = new Promise((_, reject) => 
          setTimeout(() => reject(new Error('Request timed out')), 30000)
        );
        
        // Race between the actual request and the timeout
        const result = await Promise.race([
          getDocumentPairResult(comparisonId, pairIndex),
          timeoutPromise
        ]);
        
        // Check if component is still mounted
        if (!isMounted.current) return;
        
        // Check if result is valid (has expected properties)
        if (!result || (typeof result === 'object' && Object.keys(result).length === 0)) {
          throw new Error('Received empty result from server');
        }
        
        console.log('Received document pair result:', result);
        
        setPairResult(result);
        setComparisonResult(result);
        
        // Set selected page to the first page of the document
        setSelectedPage(1);
        
        setLoadingPair(false);
      } catch (err) {
        // Check if component is still mounted
        if (!isMounted.current) return;
        
        console.error('Error loading document pair result:', err);
        
        // Check if we should retry - is it a "still processing" error?
        if (err.message.includes("still processing") && retryCount < maxRetries) {
          setPairError(`The comparison is still being processed. Retrying in ${Math.round(3 * Math.pow(1.5, retryCount))} seconds...`);
          
          setTimeout(() => {
            if (isMounted.current) {
              // Try again with the same parameters
              handleSelectDocumentPair(pairIndex, pair);
            }
          }, 3000 * Math.pow(1.5, retryCount));
        } else if (err.message === 'Request timed out') {
          setPairError('Request timed out. The server may be overloaded or the comparison is too complex.');
          setLoadingPair(false);
        } else {
          setPairError('Failed to load comparison for this document pair. The server may still be processing or the comparison failed.');
          setLoadingPair(false);
        }
      }
    } else {
      // If the pair is not matched, still set loading to false
      setLoadingPair(false);
    }
  };
  
  // Function to view the selected pair's comparison
  const viewPairComparison = () => {
    if (selectedPair && selectedPair.matched) {
      setActiveView('comparison');
    }
  };
  
  // Function to go back to document matching view
  const backToMatching = () => {
    setActiveView('matching');
  };

  return (
    <div className="smart-comparison-container">
      {activeView === 'matching' ? (
        <DocumentMatchingView 
          comparisonId={comparisonId}
          onSelectDocumentPair={handleSelectDocumentPair}
          documentPairs={localDocumentPairs}
          loading={state.loading || fetchInProgress.current}
          error={state.error}
        />
      ) : (
        <div className="comparison-view">
          <div className="comparison-header">
            <button 
              className="back-button"
              onClick={backToMatching}
            >
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z" />
              </svg>
              Back to Document List
            </button>
            
            <div className="document-info">
              {selectedPair && (
                <>
                  <h3>Document {selectedPairIndex + 1} Comparison</h3>
                  <div className="page-ranges">
                    <span className="base-range">Base: {selectedPair.baseStartPage}-{selectedPair.baseEndPage}</span>
                    <span className="separator">vs</span>
                    <span className="compare-range">Compare: {selectedPair.compareStartPage}-{selectedPair.compareEndPage}</span>
                  </div>
                </>
              )}
            </div>
            
            <div className="document-navigation">
              <button 
                className="nav-button prev"
                onClick={() => {
                  if (selectedPairIndex > 0) {
                    const newIndex = selectedPairIndex - 1;
                    handleSelectDocumentPair(newIndex, localDocumentPairs[newIndex]);
                  }
                }}
                disabled={selectedPairIndex <= 0}
              >
                <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
                </svg>
                Previous Document
              </button>
              
              <div className="document-counter">
                {selectedPairIndex + 1} / {localDocumentPairs.length}
              </div>
              
              <button 
                className="nav-button next"
                onClick={() => {
                  if (selectedPairIndex < localDocumentPairs.length - 1) {
                    const newIndex = selectedPairIndex + 1;
                    handleSelectDocumentPair(newIndex, localDocumentPairs[newIndex]);
                  }
                }}
                disabled={selectedPairIndex >= localDocumentPairs.length - 1}
              >
                Next Document
                <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
                </svg>
              </button>
            </div>
          </div>
          
          {loadingPair ? (
            <div className="pair-loading">
              <Spinner size="large" />
              <p>Loading document comparison...</p>
              {pairError && <p className="retry-message">{pairError}</p>}
            </div>
          ) : pairError && retryCount >= maxRetries ? (
            <div className="pair-error">
              <div className="error-icon">
                <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
                </svg>
              </div>
              <h3>Error Loading Comparison</h3>
              <p>{pairError}</p>
              <button 
                className="retry-button" 
                onClick={() => {
                  setRetryCount(0);
                  handleSelectDocumentPair(selectedPairIndex, selectedPair);
                }}
              >
                Try Again
              </button>
            </div>
          ) : pairResult ? (
            <div className="pair-comparison">
              <SideBySideView 
                comparisonId={comparisonId}
                result={pairResult}
                documentPair={selectedPair}
              />
            </div>
          ) : selectedPair ? (
            // Fallback: If we have a selected pair but no result, still render the SideBySideView with a minimal result object
            <div className="pair-comparison">
              <SideBySideView 
                comparisonId={comparisonId}
                result={{
                  basePageCount: selectedPair.basePageCount || 1,
                  comparePageCount: selectedPair.comparePageCount || 1,
                  documentPairs: [selectedPair],
                  fallback: true
                }}
                documentPair={selectedPair}
              />
            </div>
          ) : (
            <div className="no-pair-selected">
              <p>No document pair selected for comparison.</p>
              <button 
                className="back-button" 
                onClick={backToMatching}
              >
                Back to Document List
              </button>
            </div>
          )}
        </div>
      )}
      
      {selectedPair && selectedPair.matched && activeView === 'matching' && (
        <div className="view-comparison-bar">
          <button 
            className="view-comparison-button"
            onClick={viewPairComparison}
          >
            View Comparison for Document {selectedPairIndex + 1}
          </button>
        </div>
      )}
    </div>
  );
};

export default SmartComparisonContainer;
