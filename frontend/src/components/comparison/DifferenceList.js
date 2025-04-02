import React, { useState, useEffect } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { usePreferences } from '../../context/PreferencesContext';
import { getComparisonDetails, getDocumentPageDetails } from '../../services/api';
import Spinner from '../common/Spinner';
import './DifferenceList.css';

const DifferenceList = ({ result, onDifferenceClick, onFilterChange }) => {
  const { 
    state, 
    updateFilters 
  } = useComparison();
  
  const { preferences } = usePreferences();
  
  const [allDifferences, setAllDifferences] = useState([]);
  const [filteredDifferences, setFilteredDifferences] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [expandedPages, setExpandedPages] = useState({});
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState('all');
  const [severityFilter, setSeverityFilter] = useState('all');
  
  useEffect(() => {
    // This will store all differences found from the API
    const fetchAllDifferences = async () => {
      if (!result) return;
      
      try {
        setLoading(true);
        
        // Store differences from all pages for an overview
        const allDiffs = [];
        
        // Determine the correct page count based on whether we're in smart comparison mode
        let maxPages = 0;
        
        if (result.documentPairs && result.documentPairs.length > 0) {
          // Smart comparison mode - use the page count from the first document pair
          const firstPair = result.documentPairs[0];
          maxPages = Math.max(
            firstPair.basePageCount || 0,
            firstPair.comparePageCount || 0
          );
          console.log(`Smart comparison mode: Using max page count ${maxPages} from document pair`);
        } else {
          // Standard mode - use the overall page counts
          maxPages = Math.max(result.basePageCount || 0, result.comparePageCount || 0);
          console.log(`Standard mode: Using max page count ${maxPages}`);
        }
        
        // Store pages with differences for navigation
        const pagesWithDifferences = [];
        
        // Loop through all pages to collect differences
        for (let i = 1; i <= maxPages; i++) {
          try {
            // In smart mode, use the document pair-specific endpoint
            let pageDetails;
            if (result.documentPairs && result.documentPairs.length > 0) {
              pageDetails = await getDocumentPageDetails(state.comparisonId, 0, i);
            } else {
              pageDetails = await getComparisonDetails(state.comparisonId, i);
            }
            
            // Skip pages with "Page not found" message
            if (pageDetails && pageDetails.message && pageDetails.message.includes("Page not found")) {
              console.log(`Page ${i} not found in document pair, skipping`);
              continue;
            }
            
            let hasDifferencesOnPage = false;
            
            if (pageDetails && pageDetails.baseDifferences && pageDetails.baseDifferences.length > 0) {
              hasDifferencesOnPage = true;
              allDiffs.push(...pageDetails.baseDifferences.map(diff => ({
                ...diff,
                page: i
              })));
            }
            
            if (pageDetails && pageDetails.compareDifferences && pageDetails.compareDifferences.length > 0) {
              // Only add differences not already included from base
              const uniqueCompareDiffs = pageDetails.compareDifferences.filter(
                compDiff => !pageDetails.baseDifferences.some(
                  baseDiff => baseDiff.id === compDiff.id
                )
              );
              
              if (uniqueCompareDiffs.length > 0) {
                hasDifferencesOnPage = true;
                allDiffs.push(...uniqueCompareDiffs.map(diff => ({
                  ...diff,
                  page: i
                })));
              }
            }
            
            // If this page has differences, add it to the list of pages with differences
            if (hasDifferencesOnPage) {
              pagesWithDifferences.push(i);
              console.log(`Page ${i} has differences`);
            }
          } catch (err) {
            console.warn(`Could not load differences for page ${i}:`, err);
          }
        }
        
        console.log(`Loaded ${allDiffs.length} total differences across all pages`);
        console.log(`Pages with differences: ${pagesWithDifferences.join(', ')}`);
        
        // Store the list of pages with differences in the result object for navigation
        if (result.documentPairs && result.documentPairs.length > 0) {
          // In smart mode, add the pages with differences to the result object
          if (!result.pageDifferences) {
            result.pageDifferences = pagesWithDifferences.map(pageNumber => ({
              pageNumber,
              hasDifferences: true
            }));
            console.log("Added pageDifferences to result object for navigation:", result.pageDifferences);
          }
        }
        
        setAllDifferences(allDiffs);
        
        // Apply initial filtering
        applyFilters(allDiffs, searchTerm, typeFilter, severityFilter);
        
        setLoading(false);
      } catch (err) {
        console.error('Error fetching all differences:', err);
        setError('Failed to load differences. Please try again.');
        setLoading(false);
      }
    };
    
    fetchAllDifferences();
  }, [result, state.comparisonId]);

  useEffect(() => {
    applyFilters(allDifferences, searchTerm, typeFilter, severityFilter);
  }, [allDifferences, searchTerm, typeFilter, severityFilter]);
  const EmptyDifferenceView = () => {
    return (
      <div className="no-differences-container">
        <p className="no-differences-message">
          No differences found matching your filters.
        </p>
      </div>
    );
  };
  
  const applyFilters = (diffs, search, type, severity) => {
    if (!diffs || diffs.length === 0) {
      setFilteredDifferences([]);
      return;
    }
    
    let filtered = [...diffs];
    
    // Apply type filter
    if (type !== 'all') {
      filtered = filtered.filter(diff => diff.type === type);
    }
    
    // Apply severity filter
    if (severity !== 'all') {
      const severityLevels = ['info', 'minor', 'major', 'critical'];
      const minSeverityIndex = severityLevels.indexOf(severity);
      
      if (minSeverityIndex >= 0) {
        filtered = filtered.filter(diff => {
          const diffSeverityIndex = severityLevels.indexOf(diff.severity || 'minor');
          return diffSeverityIndex >= minSeverityIndex;
        });
      }
    }
    
    // Apply search filter
    if (search && search.trim() !== '') {
      const searchLower = search.toLowerCase().trim();
      filtered = filtered.filter(diff => 
        (diff.description && diff.description.toLowerCase().includes(searchLower)) ||
        (diff.text && diff.text.toLowerCase().includes(searchLower)) ||
        (diff.baseText && diff.baseText.toLowerCase().includes(searchLower)) ||
        (diff.compareText && diff.compareText.toLowerCase().includes(searchLower))
      );
    }
    
    setFilteredDifferences(filtered);
    console.log(`Filtered to ${filtered.length} differences`);
  };
  
  // Group differences by page
  const getDifferencesByPage = () => {
    const byPage = {};
    
    // Group metadata differences separately
    const metadataDiffs = filteredDifferences.filter(diff => diff.type === 'metadata');
    if (metadataDiffs.length > 0) {
      byPage['metadata'] = metadataDiffs;
    }
    
    // Group page differences
    filteredDifferences
      .filter(diff => diff.type !== 'metadata')
      .forEach(diff => {
        const page = diff.page || 'unknown';
        if (!byPage[page]) {
          byPage[page] = [];
        }
        byPage[page].push(diff);
      });
    
    return byPage;
  };
  
  const differencesByPage = getDifferencesByPage();
  
  // Toggle expanded state for a page group
  const togglePageExpansion = (page) => {
    setExpandedPages(prev => ({
      ...prev,
      [page]: !prev[page]
    }));
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
      case 'structure':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z" />
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
  
  // Get severity indicator
  const getSeverityIndicator = (severity) => {
    let className = 'severity-indicator';
    
    switch (severity) {
      case 'critical':
        className += ' critical';
        break;
      case 'major':
        className += ' major';
        break;
      case 'minor':
        className += ' minor';
        break;
      default:
        className += ' info';
    }
    
    return <span className={className}></span>;
  };

  return (
    <div className="difference-list-container">
      <div className="difference-controls">
        <div className="search-control">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z" />
          </svg>
          <input 
            type="text" 
            placeholder="Search differences..." 
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          {searchTerm && (
            <button 
              className="clear-search" 
              onClick={() => setSearchTerm('')}
              title="Clear search"
            >
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
              </svg>
            </button>
          )}
        </div>
        
        <div className="filter-controls">
          <div className="filter-group">
            <label htmlFor="typeFilter">Type:</label>
            <select 
              id="typeFilter"
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
            >
              <option value="all">All Types</option>
              <option value="text">Text</option>
              <option value="image">Images</option>
              <option value="font">Fonts</option>
              <option value="style">Styles</option>
              <option value="metadata">Metadata</option>
              <option value="structure">Structure</option>
            </select>
          </div>
          
          <div className="filter-group">
            <label htmlFor="severityFilter">Severity:</label>
            <select 
              id="severityFilter"
              value={severityFilter}
              onChange={(e) => setSeverityFilter(e.target.value)}
            >
              <option value="all">All Severities</option>
              <option value="critical">Critical Only</option>
              <option value="major">Major & Critical</option>
              <option value="minor">Minor & Above</option>
            </select>
          </div>
        </div>
      </div>
      
      <div className="difference-count">
        Found {filteredDifferences.length} difference{filteredDifferences.length !== 1 ? 's' : ''}
      </div>
      
      {loading ? (
        <div className="difference-list-loading">
          <Spinner size="medium" />
          <p>Loading differences...</p>
        </div>
      ) : error ? (
        <div className="difference-list-error">
          <div className="error-icon">
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
            </svg>
          </div>
          <p>{error}</p>
        </div>
      ) : Object.keys(differencesByPage).length === 0 ? (
        <div className="no-differences">
          <p>No differences found matching your filters.</p>
        </div>
      ) : (
        <div className="differences-by-page">
          {/* Metadata differences */}
          {differencesByPage['metadata'] && (
            <div className="page-group metadata-group">
              <div 
                className="page-header"
                onClick={() => togglePageExpansion('metadata')}
              >
                <div className="page-toggle">
                  {expandedPages['metadata'] ? '▼' : '►'}
                </div>
                <div className="page-title">
                  <h3>Document Metadata</h3>
                  <span className="difference-badge">
                    {differencesByPage['metadata'].length}
                  </span>
                </div>
              </div>
              
              {expandedPages['metadata'] && (
                <div className="page-differences">
                  {differencesByPage['metadata'].map((diff) => (
                    <div 
                      key={diff.id}
                      className={`difference-item ${diff.type}`}
                      onClick={() => onDifferenceClick(diff)}
                    >
                      <div className="difference-header">
                        <div className="difference-type">
                          <span className={`type-icon ${diff.type || 'unknown'}`}>
                            {getTypeIcon(diff.type || 'unknown')}
                          </span>
                        </div>
                        
                        {getSeverityIndicator(diff.severity)}
                        
                        <div className="difference-description">
                          <strong>{diff.key}:</strong> {diff.baseValue || '(empty)'} → {diff.compareValue || '(empty)'}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
          
          {/* Page differences */}
          {Object.entries(differencesByPage)
            .filter(([page]) => page !== 'metadata')
            .sort(([pageA], [pageB]) => {
              // Handle 'unknown' page specially
              if (pageA === 'unknown') return 1;
              if (pageB === 'unknown') return -1;
              return parseInt(pageA) - parseInt(pageB);
            })
            .map(([page, diffs]) => (
              <div key={page} className="page-group">
                <div 
                  className="page-header"
                  onClick={() => togglePageExpansion(page)}
                >
                  <div className="page-toggle">
                    {expandedPages[page] ? '▼' : '►'}
                  </div>
                  <div className="page-title">
                    <h3>Page {page}</h3>
                    <span className="difference-badge">
                      {diffs.length}
                    </span>
                  </div>
                </div>
                
                {expandedPages[page] && (
                  <div className="page-differences">
                    {diffs.map((diff) => (
                      <div 
                        key={diff.id}
                        className={`difference-item ${diff.type}`}
                        onClick={() => onDifferenceClick(diff)}
                      >
                        <div className="difference-header">
                          <div className="difference-type">
                            <span className={`type-icon ${diff.type || 'unknown'}`}>
                              {getTypeIcon(diff.type || 'unknown')}
                            </span>
                          </div>
                          
                          {getSeverityIndicator(diff.severity)}
                          
                          <div className="difference-description">
                            {diff.description || (
                              diff.type === 'text' ? (
                                <>
                                  <strong>Text:</strong> {diff.baseText || ''} → {diff.compareText || ''}
                                </>
                              ) : diff.type === 'image' ? (
                                <>
                                  <strong>Image:</strong> {diff.imageName || 'Unnamed image'}
                                </>
                              ) : diff.type === 'font' ? (
                                <>
                                  <strong>Font:</strong> {diff.fontName || 'Font difference'}
                                </>
                              ) : diff.type === 'style' ? (
                                <>
                                  <strong>Style:</strong> {diff.text ? `"${diff.text}"` : 'Style difference'}
                                </>
                              ) : 'Difference detected'
                            )}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ))
          }
        </div>
      )}
    </div>
  );
};

export default DifferenceList;
