import React, { useState, useEffect, useRef, memo } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { getComparisonResult, generateReport, downloadBlob } from '../../services/api';
import ComparisonSummary from './ComparisonSummary';
import SideBySideView from '../comparison/SideBySideView'; // This will now use the simplified version
import Spinner from '../common/Spinner';
import './ResultViewer.css';

// Error component
const ResultViewerError = memo(({ error, onRetry, onNewComparison }) => {
  return (
    <div className="result-viewer-error">
      <div className="error-icon">
        <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
        </svg>
      </div>
      <h3>Error Loading Comparison Results</h3>
      <p className="error-message">{error}</p>
      <p className="error-details">
        The comparison process may have failed or the server is not responding.
      </p>
      <div className="error-actions">
        <button className="reload-button" onClick={onRetry}>
          Retry
        </button>
        <button className="back-button" onClick={onNewComparison}>
          Start New Comparison
        </button>
      </div>
    </div>
  );
});

// Memoized ComparisonSummary
const MemoizedSummary = memo(ComparisonSummary);

// Main ResultViewer component - optimized to prevent render loops
const ResultViewer = memo(({ comparisonId, onNewComparison }) => {
  console.log("ResultViewer rendering with comparisonId:", comparisonId);
  
  // State with initializers to prevent default value issues
  const [activeTab, setActiveTab] = useState('overview');
  const [exportFormat, setExportFormat] = useState('pdf');
  const [exportLoading, setExportLoading] = useState(false);
  const [retryCount, setRetryCount] = useState(0);
  const [maxRetries] = useState(20);
  
  // Use refs for values we need to track without triggering rerenders
  const retryCountRef = useRef(retryCount);
  const timerRef = useRef(null);
  const fetchingRef = useRef(false);
  
  // Context
  const {
    state,
    setComparisonResult,
    setLoading,
    setError,
    setSelectedPage,
    setSelectedDifference
  } = useComparison();

  // Update ref when retryCount changes
  useEffect(() => {
    retryCountRef.current = retryCount;
  }, [retryCount]);

  // Cleanup timers on unmount
  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, []);

  // Fetch comparison results - optimized to prevent loops
  const fetchResults = async () => {
    if (!comparisonId || fetchingRef.current) return;
    
    try {
      fetchingRef.current = true;
      setLoading(true);
      setError(null);
      
      console.log(`Fetching comparison results for ID: ${comparisonId} (Attempt ${retryCountRef.current + 1}/${maxRetries})`);
      
      const result = await getComparisonResult(comparisonId);
      
      // If we got a valid result
      if (result) {
        console.log('Comparison result received:', result);
        setComparisonResult(result);
        setLoading(false);
        setRetryCount(0);
        fetchingRef.current = false;
        return;
      } else {
        throw new Error("Empty result received from server");
      }
    } catch (err) {
      console.error('Error fetching comparison results:', err);
      
      // If still processing and under max retries
      if (err.message && err.message.includes("still processing") && retryCountRef.current < maxRetries) {
        // Exponential backoff - increase delay with each retry
        const delay = Math.min(2000 * Math.pow(1.5, retryCountRef.current), 15000);
        
        setError(`Comparison result not available yet. Retrying in ${Math.round(delay/1000)} seconds...`);
        setLoading(false);
        
        // Clear any existing timer
        if (timerRef.current) {
          clearTimeout(timerRef.current);
        }
        
        // Set up next retry
        timerRef.current = setTimeout(() => {
          setRetryCount(prev => prev + 1);
        }, delay);
      } else {
        // Max retries reached or different error
        setError(err.message || 'Failed to load comparison results after multiple attempts.');
        setLoading(false);
      }
      
      fetchingRef.current = false;
    }
  };

  // Effect to trigger retries
  useEffect(() => {
    if (retryCount > 0 || (!state.comparisonResult && !state.error)) {
      fetchResults();
    }
  }, [retryCount, comparisonId]);

  // Initial fetch
  useEffect(() => {
    if (comparisonId) {
      setRetryCount(0);
      setError(null);
      setComparisonResult(null);
      fetchResults();
    }
  }, [comparisonId]);

  // Handle export with memoization
  const handleExport = async () => {
    try {
      setExportLoading(true);
      
      const blob = await generateReport(comparisonId, exportFormat);
      
      // Generate filename based on base and compare filenames
      const baseFileName = state.baseFile?.fileName?.replace('.pdf', '') || 'base';
      const compareFileName = state.compareFile?.fileName?.replace('.pdf', '') || 'compare';
      const fileName = `${baseFileName}_vs_${compareFileName}_comparison.${exportFormat}`;
      
      downloadBlob(blob, fileName);
      
      setExportLoading(false);
    } catch (err) {
      console.error('Error exporting report:', err);
      setError(err.response?.data?.error || 'Failed to export report.');
      setExportLoading(false);
    }
  };

  // Select difference with memoization 
  const handleDifferenceClick = (diffInfo) => {
    // Set the selected page and difference
    if (diffInfo.page) {
      setSelectedPage(diffInfo.page);
    }
    setSelectedDifference(diffInfo);
    
    // Switch to side-by-side view
    setActiveTab('sideBySide');
  };

  // Initial loading state
  if (state.loading && !state.comparisonResult && retryCount === 0) {
    return (
      <div className="result-viewer-loading">
        <Spinner size="large" />
        <p>Loading comparison results...</p>
      </div>
    );
  }

  // Error state after max retries
  if (state.error && retryCount >= maxRetries) {
    return (
      <ResultViewerError 
        error={state.error}
        onRetry={() => {
          setRetryCount(0);
          fetchResults();
        }}
        onNewComparison={onNewComparison}
      />
    );
  }

  // Processing state - still retrying
  if (!state.comparisonResult && retryCount < maxRetries) {
    return (
      <div className="result-viewer-loading">
        <Spinner size="large" />
        <p>Processing comparison... {retryCount > 0 ? `(Attempt ${retryCount}/${maxRetries})` : ''}</p>
        {state.error && <p className="retry-message">{state.error}</p>}
      </div>
    );
  }

  // Empty state - no results available
  if (!state.comparisonResult) {
    return (
      <div className="result-viewer-empty">
        <p>No comparison results available.</p>
        <button className="back-button" onClick={onNewComparison}>
          Start New Comparison
        </button>
      </div>
    );
  }

  // Main view - comparison results available
  return (
    <div className="result-viewer">
      <div className="result-header">
        <div className="result-info">
          <h2>Comparison Results</h2>
          <div className="document-info">
            <span className="document-name">{state.baseFile?.fileName}</span>
            <span className="vs">vs</span>
            <span className="document-name">{state.compareFile?.fileName}</span>
          </div>
        </div>
        
        <div className="result-actions">
          <div className="export-controls">
            <select 
              value={exportFormat}
              onChange={(e) => setExportFormat(e.target.value)}
              disabled={exportLoading}
            >
              <option value="pdf">PDF Report</option>
              <option value="html">HTML Report</option>
              <option value="json">JSON Data</option>
            </select>
            <button 
              className="export-button"
              onClick={handleExport}
              disabled={exportLoading}
            >
              {exportLoading ? 'Exporting...' : 'Export Report'}
            </button>
          </div>
          
          <button className="new-comparison-button" onClick={onNewComparison}>
            New Comparison
          </button>
        </div>
      </div>
      
      <div className="result-tabs">
        <button 
          className={`tab-button ${activeTab === 'overview' ? 'active' : ''}`}
          onClick={() => setActiveTab('overview')}
        >
          Overview
        </button>
        <button 
          className={`tab-button ${activeTab === 'sideBySide' ? 'active' : ''}`}
          onClick={() => setActiveTab('sideBySide')}
        >
          Side by Side
        </button>
      </div>
      
      <div className="result-content">
        {activeTab === 'overview' && (
          <MemoizedSummary 
            result={state.comparisonResult}
            onDifferenceClick={handleDifferenceClick}
          />
        )}
        
        {activeTab === 'sideBySide' && (
          <SideBySideView 
            comparisonId={comparisonId}
          />
        )}
      </div>
    </div>
  );
});

export default ResultViewer;