import React, { useState, useEffect, useRef } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { getComparisonResult, generateReport, downloadBlob } from '../../services/api';
import SummaryPanel from './SummaryPanel';
import SideBySideView from '../comparison/SideBySideView';
import DifferenceList from '../comparison/DifferenceList';
import Spinner from '../common/Spinner';
import './ResultViewer.css';

const ResultViewer = ({ comparisonId, onNewComparison }) => {
  const { 
    state, 
    setComparisonResult, 
    setLoading, 
    setError, 
    setSelectedPage,
    setSelectedDifference,
    updateFilters 
  } = useComparison();
  
  const [activeTab, setActiveTab] = useState('overview');
  const [exportFormat, setExportFormat] = useState('pdf');
  const [exportLoading, setExportLoading] = useState(false);
  const [retryCount, setRetryCount] = useState(0);
  const [maxRetries] = useState(5);
  
  // Use refs to prevent infinite loops
  const retryCountRef = useRef(retryCount);
  const timerRef = useRef(null);
  
  // Update ref when state changes
  useEffect(() => {
    retryCountRef.current = retryCount;
  }, [retryCount]);

  // Cleanup any pending timers when component unmounts
  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, []);

  // Main fetch function - no useCallback to avoid dependency issues
  const fetchResults = async () => {
    if (!comparisonId) return;
    
    try {
      setLoading(true);
      
      console.log(`Fetching comparison results for ID: ${comparisonId} (Attempt ${retryCountRef.current + 1}/${maxRetries})`);
      
      const result = await getComparisonResult(comparisonId);
      
      // If we got a result, reset everything and update state
      setComparisonResult(result);
      setLoading(false);
      setRetryCount(0);
      console.log('Comparison result received:', result);
    } catch (err) {
      console.error('Error fetching comparison results:', err);
      
      if (retryCountRef.current < maxRetries - 1) {
        // Increase the delay with each retry (exponential backoff)
        const delay = Math.min(2000 * Math.pow(1.5, retryCountRef.current), 15000);
        console.log(`Retrying in ${Math.round(delay/1000)} seconds...`);
        
        setError(`Comparison result not available yet. Retrying in ${Math.round(delay/1000)} seconds...`);
        setLoading(false);
        
        // Clear any existing timer
        if (timerRef.current) {
          clearTimeout(timerRef.current);
        }
        
        // Set up next retry using the ref
        timerRef.current = setTimeout(() => {
          setRetryCount(prev => prev + 1);
          fetchResults(); // Call directly instead of relying on effect
        }, delay);
      } else {
        setError('Failed to load comparison results after multiple attempts. The comparison may still be processing or has failed.');
        setLoading(false);
      }
    }
  };

  // Effect to fetch results when component mounts or comparison ID changes
  useEffect(() => {
    if (comparisonId) {
      // Reset state for new comparison
      setRetryCount(0);
      setError(null);
      setComparisonResult(null);
      
      // Clear any existing timer
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      
      // Start fetching
      fetchResults();
    }
    
    // Cleanup function
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, [comparisonId]); // Only depend on comparisonId

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

  const handleDifferenceClick = (diffInfo) => {
    // Set the selected page and difference
    setSelectedPage(diffInfo.page);
    setSelectedDifference(diffInfo);
    
    // Switch to side-by-side view
    setActiveTab('sideBySide');
  };

  const handleFilterChange = (filters) => {
    updateFilters(filters);
  };

  if (state.loading && retryCount === 0) {
    return (
      <div className="result-viewer-loading">
        <Spinner size="large" />
        <p>Loading comparison results...</p>
      </div>
    );
  }

  if (state.error && retryCount >= maxRetries) {
    return (
      <div className="result-viewer-error">
        <div className="error-icon">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
        </div>
        <h3>Error Loading Results</h3>
        <p>{state.error}</p>
        <button className="reload-button" onClick={() => {
          setRetryCount(0);
          fetchResults();
        }}>
          Try Again
        </button>
        <button className="back-button" onClick={onNewComparison}>
          Start New Comparison
        </button>
      </div>
    );
  }

  if (!state.comparisonResult && retryCount < maxRetries) {
    return (
      <div className="result-viewer-loading">
        <Spinner size="large" />
        <p>Processing comparison... {retryCount > 0 ? `(Attempt ${retryCount}/${maxRetries})` : ''}</p>
        {state.error && <p className="retry-message">{state.error}</p>}
      </div>
    );
  }

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
        <button 
          className={`tab-button ${activeTab === 'differences' ? 'active' : ''}`}
          onClick={() => setActiveTab('differences')}
        >
          Differences
        </button>
      </div>
      
      <div className="result-content">
        {activeTab === 'overview' && (
          <SummaryPanel 
            result={state.comparisonResult}
            onDifferenceClick={handleDifferenceClick}
          />
        )}
        

        {activeTab === 'sideBySide' && (
            {result.mode === 'smart' ? (
                <SmartComparisonContainer 
                  comparisonId={comparisonId}
                />
            ) : (
          <SideBySideView 
            comparisonId={comparisonId}
            result={state.comparisonResult}
          />
        )}
    )}
        {activeTab === 'differences' && (
          <DifferenceList 
            result={state.comparisonResult}
            onDifferenceClick={handleDifferenceClick}
            onFilterChange={handleFilterChange}
          />
        )}
      </div>
    </div>
  );
};

export default ResultViewer;