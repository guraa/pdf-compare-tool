// Updated ComparisonSummary.js component
// This fixes the issue with difference type categorization in the summary view

import React, { useMemo } from 'react';
import './ComparisonSummary.css';

const ComparisonSummary = ({ result }) => {
  // Use memoization to process the result data efficiently
  const processedData = useMemo(() => {
    if (!result) {
      return {
        basePageCount: 0,
        comparePageCount: 0,
        totalDifferences: 0,
        differentPagesCount: 0,
        textDifferenceCount: 0,
        imageDifferenceCount: 0,
        fontDifferenceCount: 0,
        styleDifferenceCount: 0,
        structureCount: 0,
        categoryCounts: {}
      };
    }

    // Extract basic counts
    const basePageCount = result.summary?.baseTotalPages || 0;
    const comparePageCount = result.summary?.compareTotalPages || 0;
    const totalDifferences = result.totalDifferences || 0;
    
    // Process differences by type
    let textCount = 0;
    let imageCount = 0;
    let fontCount = 0;
    let styleCount = 0;
    let structureCount = 0;
    
    // Process all differences from all pages
    const processAllDifferences = () => {
      // Collect all differences into a single array
      let allDiffs = [];
      
      if (result.differencesByPage) {
        Object.values(result.differencesByPage).forEach(pageDiffs => {
          if (Array.isArray(pageDiffs)) {
            allDiffs = [...allDiffs, ...pageDiffs];
          }
        });
      }
      
      // Count by type
      allDiffs.forEach(diff => {
        switch (diff.type) {
          case 'text':
            textCount++;
            break;
          case 'image':
            imageCount++;
            break;
          case 'font':
            fontCount++;
            break;
          case 'style':
            styleCount++;
            break;
          default:
            // If type is not one of the standard types, count as structure
            structureCount++;
        }
      });
    };
    
    // Call the function to process all differences
    processAllDifferences();
    
    // If no specific type counts, allocate all differences to structure
    // This is a fallback if the difference data doesn't have proper typing
    if (textCount === 0 && imageCount === 0 && fontCount === 0 && styleCount === 0 && totalDifferences > 0) {
      structureCount = totalDifferences;
    }
    
    // Count different pages
    let differentPages = 0;
    
    if (result.pagePairs) {
      // Count page pairs that have differences
      differentPages = result.pagePairs.filter(pair => {
        // Check if this page pair has any differences
        if (result.differencesByPage && result.differencesByPage[pair.id]) {
          return result.differencesByPage[pair.id].length > 0;
        }
        return false;
      }).length;
    }
    
    return {
      basePageCount,
      comparePageCount,
      totalDifferences,
      differentPagesCount: differentPages,
      textDifferenceCount: textCount,
      imageDifferenceCount: imageCount,
      fontDifferenceCount: fontCount,
      styleDifferenceCount: styleCount,
      structureCount: structureCount,
      // Create an object with all category counts for the chart
      categoryCounts: {
        content: textCount,
        style: styleCount,
        images: imageCount,
        fonts: fontCount,
        structure: structureCount
      }
    };
  }, [result]);
  
  // Format current timestamp
  const timestamp = new Date().toLocaleString();
  
  // Extract values from processed data
  const {
    basePageCount,
    comparePageCount,
    totalDifferences,
    differentPagesCount,
    textDifferenceCount,
    imageDifferenceCount,
    fontDifferenceCount,
    styleDifferenceCount,
    structureCount,
    categoryCounts
  } = processedData;

  // If no result data available
  if (!result) {
    return (
      <div className="summary-panel empty">
        <p>No comparison data available.</p>
      </div>
    );
  }

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
                <span className="base">{basePageCount}</span>
                <span className="separator">/</span>
                <span className="compare">{comparePageCount}</span>
              </div>
              {basePageCount !== comparePageCount && (
                <div className="stat-alert">Page count mismatch!</div>
              )}
            </div>
            
            <div className="stat-item">
              <div className="stat-label">Total Differences</div>
              <div className="stat-value highlight">
                {totalDifferences}
              </div>
            </div>
            
            <div className="stat-item">
              <div className="stat-label">Different Pages</div>
              <div className="stat-value">
                {differentPagesCount}
              </div>
            </div>
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
                <div className="category-count">{textDifferenceCount} differences</div>
              </div>
            </div>
            
            <div className="category images">
              <div className="category-icon image"></div>
              <div className="category-info">
                <div className="category-label">Images</div>
                <div className="category-count">{imageDifferenceCount} differences</div>
              </div>
            </div>
            
            <div className="category fonts">
              <div className="category-icon font"></div>
              <div className="category-info">
                <div className="category-label">Fonts</div>
                <div className="category-count">{fontDifferenceCount} differences</div>
              </div>
            </div>
            
            <div className="category styles">
              <div className="category-icon style"></div>
              <div className="category-info">
                <div className="category-label">Text Styles</div>
                <div className="category-count">{styleDifferenceCount} differences</div>
              </div>
            </div>
          </div>
        </div>
        
        {/* Categories Visualization */}
        <div className="summary-card category-card">
          <h4>Difference Categories</h4>
          
          <div className="category-chart">
            <div className="chart-bars">
              {Object.entries(categoryCounts).map(([category, count]) => (
                <div className="chart-item" key={category}>
                  <div className="chart-label">{
                    category.charAt(0).toUpperCase() + category.slice(1)
                  }</div>
                  <div className="chart-bar-container">
                    <div 
                      className={`chart-bar ${category}`}
                      style={{ 
                        width: `${Math.min(100, count === 0 ? 0 : Math.max(5, count / (totalDifferences || 1) * 100))}%` 
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