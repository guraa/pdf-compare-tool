import React from 'react';
import PDFRenderer from '../PDFRenderer';

const SideBySidePanel = ({
  result,
  pageDetails,
  baseContainerRef,
  compareContainerRef,
  loading,
  state,
  handleScroll,
  handleDifferenceSelect,
  showDifferencePanel
}) => {
  return (
    <div className={`documents-container ${showDifferencePanel ? 'with-panel' : 'full-width'}`}>
      <div className="document-viewer base-document">
        <div className="document-header">
          <h3>Base Document</h3>
          <div className="document-info">
            {result.basePageCount < state.selectedPage ? (
              <span className="page-missing">Page {state.selectedPage} does not exist</span>
            ) : null}
          </div>
        </div>
        
        <div 
          ref={baseContainerRef}
          className="document-content"
          onScroll={(e) => handleScroll(e, 'base')}
        >
          {result.basePageCount >= state.selectedPage && (
            <PDFRenderer 
              fileId={state.baseFile?.fileId}
              page={state.selectedPage}
              zoom={state.viewSettings.zoom}
              highlightMode={state.viewSettings.highlightMode}
              differences={pageDetails?.baseDifferences || []}
              selectedDifference={state.selectedDifference}
              onDifferenceSelect={handleDifferenceSelect}
              loading={loading}
              interactive={true}
            />
          )}
        </div>
      </div>
      
      <div className="document-viewer compare-document">
        <div className="document-header">
          <h3>Comparison Document</h3>
          <div className="document-info">
            {result.comparePageCount < state.selectedPage ? (
              <span className="page-missing">Page {state.selectedPage} does not exist</span>
            ) : null}
          </div>
        </div>
        
        <div 
          ref={compareContainerRef}
          className="document-content"
          onScroll={(e) => handleScroll(e, 'compare')}
        >
          {result.comparePageCount >= state.selectedPage && (
            <PDFRenderer 
              fileId={state.compareFile?.fileId}
              page={state.selectedPage}
              zoom={state.viewSettings.zoom}
              highlightMode={state.viewSettings.highlightMode}
              differences={pageDetails?.compareDifferences || []}
              selectedDifference={state.selectedDifference}
              onDifferenceSelect={handleDifferenceSelect}
              loading={loading}
              interactive={true}
            />
          )}
        </div>
      </div>
    </div>
  );
};

export default SideBySidePanel;