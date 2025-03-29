// File: frontend/src/components/results/ComparisonSummary.js
import React from 'react';
import './ComparisonSummary.css';

const ComparisonSummary = ({ result }) => {
  if (!result) {
    return (
      <div className="summary-panel empty">
        <p>No comparison data available.</p>
      </div>
    );
  }

  const timestamp = new Date().toLocaleString();
  
  // Calculate difference breakdown
  const calculateTextDifferenceBreakdown = () => {
    if (!result.pageDifferences) return { added: 0, deleted: 0, modified: 0 };
    
    const breakdown = { added: 0, deleted: 0, modified: 0 };
    
    result.pageDifferences.forEach(page => {
      if (page.textDifferences?.differences) {
        page.textDifferences.differences.forEach(diff => {
          if (diff.differenceType === 'ADDED') {
            breakdown.added++;
          } else if (diff.differenceType === 'DELETED') {
            breakdown.deleted++;
          } else if (diff.differenceType === 'MODIFIED') {
            breakdown.modified++;
          }
        });
      }
    });
    
    return breakdown;
  };
  
  const textBreakdown = calculateTextDifferenceBreakdown();
  
  // Calculate page with most differences
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

  return (
    <div className="comparison-summary">
      <div className="summary-header">
        <h3>Comparison Summary</h3>
        <div className="summary-date">{timestamp}</div>
      </div>
      
      <div className="summary-grid">
        {/* Overview Card */}
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
              <div className="stat-item">
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
        
        {/* Difference Breakdown */}
        <div className="summary-card breakdown-card">
          <h4>Difference Breakdown</h4>
          
          <div className="category-breakdown">
            <div className="category text-content">
              <div className="category-icon text"></div>
              <div className="category-info">
                <div className="category-label">Text Content</div>
                <div className="category-count">{result.totalTextDifferences || 0} differences</div>
              </div>
              
              {result.totalTextDifferences > 0 && (
                <div className="text-breakdown">
                  <div className="breakdown-item">
                    <div className="breakdown-label">Added</div>
                    <div className="breakdown-value added">{textBreakdown.added}</div>
                  </div>
                  <div className="breakdown-item">
                    <div className="breakdown-label">Deleted</div>
                    <div className="breakdown-value deleted">{textBreakdown.deleted}</div>
                  </div>
                  <div className="breakdown-item">
                    <div className="breakdown-label">Modified</div>
                    <div className="breakdown-value modified">{textBreakdown.modified}</div>
                  </div>
                </div>
              )}
            </div>
            
            <div className="category images">
              <div className="category-icon image"></div>
              <div className="category-info">
                <div className="category-label">Images</div>
                <div className="category-count">{result.totalImageDifferences || 0} differences</div>
              </div>
            </div>
            
            <div className="category fonts">
              <div className="category-icon font"></div>
              <div className="category-info">
                <div className="category-label">Fonts</div>
                <div className="category-count">{result.totalFontDifferences || 0} differences</div>
              </div>
            </div>
            
            <div className="category styles">
              <div className="category-icon style"></div>
              <div className="category-info">
                <div className="category-label">Text Styles</div>
                <div className="category-count">{result.totalStyleDifferences || 0} differences</div>
              </div>
            </div>
          </div>
        </div>
        
        {/* Categories Visualization */}
        <div className="summary-card category-card">
          <h4>Difference Categories</h4>
          
          <div className="category-chart">
            <div className="chart-bars">
              {Object.entries({
                content: result.totalTextDifferences || 0,
                style: result.totalStyleDifferences || 0,
                images: result.totalImageDifferences || 0,
                structure: result.pageDifferences?.filter(p => p.onlyInBase || p.onlyInCompare || p.dimensionsDifferent).length || 0
              }).map(([category, count]) => (
                <div className="chart-item" key={category}>
                  <div className="chart-label">{
                    category.charAt(0).toUpperCase() + category.slice(1)
                  }</div>
                  <div className="chart-bar-container">
                    <div 
                      className={`chart-bar ${category}`}
                      style={{ 
                        width: `${Math.min(100, count === 0 ? 0 : Math.max(5, count / (result.totalDifferences || 1) * 100))}%` 
                      }}
                    ></div>
                  </div>
                  <div className="chart-value">{count}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ComparisonSummary;