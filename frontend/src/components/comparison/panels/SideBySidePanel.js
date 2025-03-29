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
  showDifferencePanel,
  selectedPairIndex = 0,
  isSmartMode = false
}) => {
  console.log("SideBySidePanel rendering with result:", result);
  console.log("isSmartMode:", isSmartMode);
  
  // Get the document pair if in smart mode
  const documentPair = isSmartMode && result.documentPairs && result.documentPairs.length > 0 ? 
    result.documentPairs[selectedPairIndex] : null;
  
  console.log("documentPair:", documentPair);
  
  // Calculate the actual page in base and compare documents
  // Make sure page numbers start at 1, not 0
  let selectedPage = state.selectedPage || 1;
  
  let basePage = selectedPage;
  let comparePage = selectedPage;
  
  if (isSmartMode && documentPair) {
    // Adjust for page offsets in document pairs
    basePage = documentPair.baseStartPage + selectedPage - 1;
    comparePage = documentPair.compareStartPage + selectedPage - 1;
  }
  
  // Ensure we never send page 0 to the API
  basePage = Math.max(1, basePage);
  comparePage = Math.max(1, comparePage);
  
  console.log(`Calculated page numbers - base: ${basePage}, compare: ${comparePage}`);
  
  // Check if pages exist
  const basePageExists = isSmartMode && documentPair ? 
    (basePage >= documentPair.baseStartPage && basePage <= documentPair.baseEndPage) :
    (result && result.basePageCount >= selectedPage);
    
  const comparePageExists = isSmartMode && documentPair ? 
    (comparePage >= documentPair.compareStartPage && comparePage <= documentPair.compareEndPage) :
    (result && result.comparePageCount >= selectedPage);
    
  console.log(`Page exists - base: ${basePageExists}, compare: ${comparePageExists}`);

  return (
    <div className={`documents-container ${showDifferencePanel ? 'with-panel' : 'full-width'}`}>
      <div className="document-viewer base-document">
        <div className="document-header">
          <h3>Base Document</h3>
          <div className="document-info">
            {!basePageExists && (
              <span className="page-missing">Page {selectedPage} does not exist</span>
            )}
          </div>
        </div>
        
        <div 
          ref={baseContainerRef}
          className="document-content"
          onScroll={(e) => handleScroll(e, 'base')}
        >
          {basePageExists && (
            <PDFRenderer 
              fileId={state.baseFile?.fileId}
              page={basePage}
              zoom={state.viewSettings?.zoom || 1}
              highlightMode={state.viewSettings?.highlightMode || 'all'}
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
            {!comparePageExists && (
              <span className="page-missing">Page {selectedPage} does not exist</span>
            )}
          </div>
        </div>
        
        <div 
          ref={compareContainerRef}
          className="document-content"
          onScroll={(e) => handleScroll(e, 'compare')}
        >
          {comparePageExists && (
            <PDFRenderer 
              fileId={state.compareFile?.fileId}
              page={comparePage}
              zoom={state.viewSettings?.zoom || 1}
              highlightMode={state.viewSettings?.highlightMode || 'all'}
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