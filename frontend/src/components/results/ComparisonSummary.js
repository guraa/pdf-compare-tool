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
  
  // Check if we're in smart comparison mode
  const isSmartMode = result.documentPairs && result.documentPairs.length > 0;
  
  // Extract statistics from document pairs if in smart mode
  let textDifferenceCount = 0;
  let imageDifferenceCount = 0;
  let fontDifferenceCount = 0;
  let styleDifferenceCount = 0;
  let basePageCount = 0;
  let comparePageCount = 0;
  let differentPagesCount = 0;
  
  if (isSmartMode) {
    // Aggregate stats from all document pairs
    result.documentPairs.forEach(pair => {
      textDifferenceCount += pair.textDifferences || 0;
      imageDifferenceCount += pair.imageDifferences || 0;
      fontDifferenceCount += pair.fontDifferences || 0;
      styleDifferenceCount += pair.styleDifferences || 0;
      
      // Assuming the total page counts from all pairs
      basePageCount += pair.basePageCount || 0;
      comparePageCount += pair.comparePageCount || 0;
      
      // For now, just count all pages as different if there are differences
      if (pair.totalDifferences > 0) {
        differentPagesCount += Math.max(pair.basePageCount || 0, pair.comparePageCount || 0);
      }
    });
  } else {
    // Use traditional structure
    textDifferenceCount = result.totalTextDifferences || 0;
    imageDifferenceCount = result.totalImageDifferences || 0;
    fontDifferenceCount = result.totalFontDifferences || 0;
    styleDifferenceCount = result.totalStyleDifferences || 0;
    basePageCount = result.basePageCount || 0;
    comparePageCount = result.comparePageCount || 0;
    differentPagesCount = result.pageDifferences?.filter(page => 
      page.onlyInBase || 
      page.onlyInCompare || 
      page.textDifferences?.differences?.length > 0 ||
      page.textElementDifferences?.length > 0 ||
      page.imageDifferences?.length > 0 ||
      page.fontDifferences?.length > 0
    ).length || 0;
  }
  
  // Total differences remains the same in both modes
  const totalDifferences = result.totalDifferences || 0;

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
              {Object.entries({
                content: textDifferenceCount,
                style: styleDifferenceCount,
                images: imageDifferenceCount,
                fonts: fontDifferenceCount,
                structure: Math.max(0, totalDifferences - textDifferenceCount - styleDifferenceCount - imageDifferenceCount - fontDifferenceCount)
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
