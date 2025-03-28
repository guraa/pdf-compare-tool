import React from 'react';
import DifferenceStats from './DifferenceStats';
import { usePreferences } from '../../context/PreferencesContext';
import './SummaryPanel.css';

const SummaryPanel = ({ result, onDifferenceClick }) => {
  const { preferences } = usePreferences();
  
  // Calculate the page with most differences
  const findPageWithMostDifferences = () => {
    if (!result || !result.pageDifferences) return null;
    
    let maxDiffs = 0;
    let maxPage = null;
    
    result.pageDifferences.forEach(page => {
      const diffCount = (
        (page.textDifferences?.differences?.length || 0) +
        (page.textElementDifferences?.length || 0) +
        (page.imageDifferences?.length || 0) +
        (page.fontDifferences?.length || 0)
      );
      
      if (diffCount > maxDiffs) {
        maxDiffs = diffCount;
        maxPage = page;
      }
    });
    
    return { page: maxPage, count: maxDiffs };
  };
  
  const pageWithMostDiffs = findPageWithMostDifferences();
  
  // Get significant metadata differences
  const getSignificantMetadataDiffs = () => {
    if (!result || !result.metadataDifferences) return [];
    
    // Filter to key metadata differences
    const importantKeys = ['title', 'author', 'creator', 'producer', 'creationDate', 'modificationDate'];
    
    return Object.values(result.metadataDifferences)
      .filter(diff => importantKeys.includes(diff.key))
      .sort((a, b) => importantKeys.indexOf(a.key) - importantKeys.indexOf(b.key));
  };
  
  const metadataDiffs = getSignificantMetadataDiffs();

  // Group differences by type
  const categorizeDifferences = () => {
    if (!result || !result.pageDifferences) return {};
    
    const categories = {
      content: 0,  // Text content differences
      style: 0,    // Text style (font, color, size) differences
      images: 0,   // Image differences
      structure: 0 // Page existence, order, dimensions
    };
    
    // Count page structure differences
    if (result.pageCountDifferent) {
      categories.structure++;
    }
    
    // Process page differences
    result.pageDifferences.forEach(page => {
      // Check if page exists only in one document
      if (page.onlyInBase || page.onlyInCompare) {
        categories.structure++;
        return;
      }
      
      // Check page dimensions
      if (page.dimensionsDifferent) {
        categories.structure++;
      }
      
      // Count text content differences
      if (page.textDifferences && page.textDifferences.differences) {
        categories.content += page.textDifferences.differences.length;
      }
      
      // Count text style differences
      if (page.textElementDifferences) {
        page.textElementDifferences.forEach(diff => {
          if (diff.styleDifferent) {
            categories.style++;
          } else {
            // If not style different, it's added or removed text
            categories.content++;
          }
        });
      }
      
      // Count image differences
      if (page.imageDifferences) {
        categories.images += page.imageDifferences.length;
      }
    });
    
    return categories;
  };
  
  const categories = categorizeDifferences();

  if (!result) {
    return (
      <div className="summary-panel empty">
        <p>No comparison data available.</p>
      </div>
    );
  }

  return (
    <div className="summary-panel">
      <div className="summary-header">
        <h3>Comparison Summary</h3>
        <div className="summary-date">
          {new Date().toLocaleString()}
        </div>
      </div>
      
      <div className="summary-grid">
        <div className="summary-card overview-card">
          <h4>Overview</h4>
          
          <div className="stat-grid">
            <div className="stat-item">
              <div className="stat-label">Total Pages</div>
              <div className="stat-value">
                <span className="base">{result.basePageCount}</span>
                <span className="separator">/</span>
                <span className="compare">{result.comparePageCount}</span>
              </div>
              {result.pageCountDifferent && (
                <div className="stat-alert">Page count mismatch!</div>
              )}
            </div>
            
            <div className="stat-item">
              <div className="stat-label">Total Differences</div>
              <div className="stat-value highlight">
                {result.totalDifferences || 0}
              </div>
            </div>
            
            <div className="stat-item">
              <div className="stat-label">Different Pages</div>
              <div className="stat-value">
                {result.pageDifferences?.filter(page => 
                  page.onlyInBase || 
                  page.onlyInCompare || 
                  page.textDifferences?.differences?.length > 0 ||
                  page.textElementDifferences?.length > 0 ||
                  page.imageDifferences?.length > 0 ||
                  page.fontDifferences?.length > 0
                ).length || 0}
              </div>
            </div>
            
            {pageWithMostDiffs && pageWithMostDiffs.count > 0 && (
              <div 
                className="stat-item clickable"
                onClick={() => onDifferenceClick({ 
                  page: pageWithMostDiffs.page.pageNumber,
                  type: 'page'
                })}
              >
                <div className="stat-label">Most Different Page</div>
                <div className="stat-value">
                  Page {pageWithMostDiffs.page.pageNumber}
                  <span className="stat-subtext">
                    ({pageWithMostDiffs.count} differences)
                  </span>
                </div>
              </div>
            )}
          </div>
        </div>
        
        <DifferenceStats 
          result={result} 
          onDifferenceClick={onDifferenceClick}
        />
        
        <div className="summary-card category-card">
          <h4>Difference Categories</h4>
          
          <div className="category-chart">
            {/* Simplified chart showing difference categories */}
            <div className="chart-bars">
              {Object.entries(categories).map(([category, count]) => (
                <div className="chart-item" key={category}>
                  <div className="chart-label">{
                    category.charAt(0).toUpperCase() + category.slice(1)
                  }</div>
                  <div className="chart-bar-container">
                    <div 
                      className={`chart-bar ${category}`}
                      style={{ 
                        width: `${Math.min(100, count === 0 ? 0 : Math.max(5, count / result.totalDifferences * 100))}%` 
                      }}
                    ></div>
                  </div>
                  <div className="chart-value">{count}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
        
        {metadataDiffs.length > 0 && (
          <div className="summary-card metadata-card">
            <h4>Metadata Differences</h4>
            
            <div className="metadata-table">
              <div className="metadata-header">
                <div className="metadata-col key">Property</div>
                <div className="metadata-col base">Base Document</div>
                <div className="metadata-col compare">Comparison Document</div>
              </div>
              
              {metadataDiffs.map((diff, index) => (
                <div 
                  className="metadata-row" 
                  key={diff.key}
                  onClick={() => onDifferenceClick({ 
                    type: 'metadata',
                    key: diff.key
                  })}
                >
                  <div className="metadata-col key">{
                    diff.key.charAt(0).toUpperCase() + diff.key.slice(1).replace(/([A-Z])/g, ' $1')
                  }</div>
                  <div className="metadata-col base">
                    {diff.onlyInCompare ? 
                      <span className="missing">Not present</span> : 
                      <span className={diff.valueDifferent ? 'different' : ''}>{diff.baseValue || '-'}</span>
                    }
                  </div>
                  <div className="metadata-col compare">
                    {diff.onlyInBase ? 
                      <span className="missing">Not present</span> : 
                      <span className={diff.valueDifferent ? 'different' : ''}>{diff.compareValue || '-'}</span>
                    }
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default SummaryPanel;