import React, { useState, useEffect } from 'react';
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

  useEffect(() => {
    const fetchResults = async () => {
      if (!comparisonId) return;
      
      try {
        setLoading(true);
        
        const result = await getComparisonResult(comparisonId);
        setComparisonResult(result);
        
        setLoading(false);
      } catch (err) {
        console.error('Error fetching comparison results:', err);
        setError(err.response?.data?.error || 'Failed to load comparison results.');
        setLoading(false);
      }
    };

    fetchResults();
  }, [comparisonId, setComparisonResult, setLoading, setError]);

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

  if (state.loading) {
    return (
      <div className="result-viewer-loading">
        <Spinner size="large" />
        <p>Loading comparison results...</p>
      </div>
    );
  }

  if (state.error) {
    return (
      <div className="result-viewer-error">
        <div className="error-icon">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
        </div>
        <h3>Error Loading Results</h3>
        <p>{state.error}</p>
        <button className="reload-button" onClick={() => window.location.reload()}>
          Reload
        </button>
        <button className="back-button" onClick={onNewComparison}>
          Start New Comparison
        </button>
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
          <SideBySideView 
            comparisonId={comparisonId}
            result={state.comparisonResult}
          />
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