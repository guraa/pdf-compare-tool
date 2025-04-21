import React, { createContext, useContext, useReducer, useEffect } from 'react';

// Initial state for the comparison context
const initialState = {
  baseFile: null,
  compareFile: null,
  comparisonId: null,
  comparisonResult: null,
  loading: false,
  error: null,
  selectedPage: 1,
  selectedDifference: null,
  selectedDocumentPairIndex: 0, // Add this to track the selected document pair
  documentPairs: [], // Add this to store all document pairs
  progress: {
    status: null,
    progress: 0,
    completedOperations: 0,
    totalOperations: 0,
    currentPhase: null
  },
  filters: {
    differenceTypes: ['text', 'font', 'image', 'style', 'metadata'],
    minSeverity: 'all', // 'all', 'minor', 'major', 'critical'
    searchTerm: ''
  },
  viewSettings: {
    highlightMode: 'all', // 'all', 'text', 'images', 'fonts', 'none'
    zoom: 0.5,
    syncScroll: true,
    showChangesOnly: false,
    enhancedDiffView: false // Add this to toggle the enhanced diff view
  }
};

// Actions for the reducer
const actions = {
  SET_BASE_FILE: 'SET_BASE_FILE',
  SET_COMPARE_FILE: 'SET_COMPARE_FILE',
  SET_COMPARISON_ID: 'SET_COMPARISON_ID',
  SET_COMPARISON_RESULT: 'SET_COMPARISON_RESULT',
  SET_LOADING: 'SET_LOADING',
  SET_ERROR: 'SET_ERROR',
  SET_SELECTED_PAGE: 'SET_SELECTED_PAGE',
  SET_SELECTED_DIFFERENCE: 'SET_SELECTED_DIFFERENCE',
  SET_DOCUMENT_PAIRS: 'SET_DOCUMENT_PAIRS',
  SET_SELECTED_DOCUMENT_PAIR_INDEX: 'SET_SELECTED_DOCUMENT_PAIR_INDEX',
  UPDATE_FILTERS: 'UPDATE_FILTERS',
  UPDATE_VIEW_SETTINGS: 'UPDATE_VIEW_SETTINGS',
  UPDATE_PROGRESS: 'UPDATE_PROGRESS',
  RESET_COMPARISON: 'RESET_COMPARISON'
};

// Reducer function to handle state updates
const comparisonReducer = (state, action) => {
  console.log('Action:', action.type, action.payload);
  
  switch (action.type) {
    case actions.SET_BASE_FILE:
      return { ...state, baseFile: action.payload };
    
    case actions.SET_COMPARE_FILE:
      return { ...state, compareFile: action.payload };
    
    case actions.SET_COMPARISON_ID:
      console.log('Setting comparison ID:', action.payload);
      return { ...state, comparisonId: action.payload };
    
    case actions.SET_COMPARISON_RESULT:
      return { ...state, comparisonResult: action.payload };
    
    case actions.SET_LOADING:
      return { ...state, loading: action.payload };
    
    case actions.SET_ERROR:
      return { ...state, error: action.payload };
    
    case actions.SET_SELECTED_PAGE:
      return { ...state, selectedPage: action.payload };
    
    case actions.SET_SELECTED_DIFFERENCE:
      return { ...state, selectedDifference: action.payload };
    
    case actions.SET_DOCUMENT_PAIRS:
      return { ...state, documentPairs: action.payload };
    
    case actions.SET_SELECTED_DOCUMENT_PAIR_INDEX:
      return { ...state, selectedDocumentPairIndex: action.payload };
    
    case actions.UPDATE_FILTERS:
      return { 
        ...state, 
        filters: { ...state.filters, ...action.payload } 
      };
    
    case actions.UPDATE_VIEW_SETTINGS:
      return { 
        ...state, 
        viewSettings: { ...state.viewSettings, ...action.payload } 
      };
    
    case actions.UPDATE_PROGRESS:
      return {
        ...state,
        progress: { ...state.progress, ...action.payload }
      };
    
    case actions.RESET_COMPARISON:
      return { 
        ...initialState,
        // Keep view settings and filters across resets
        viewSettings: state.viewSettings,
        filters: state.filters
      };
    
    default:
      throw new Error(`Unhandled action type: ${action.type}`);
  }
};

// Create the context
const ComparisonContext = createContext();

// Create the context provider component
export const ComparisonProvider = ({ children }) => {
  const [state, dispatch] = useReducer(comparisonReducer, initialState);

  // Define helper functions to dispatch actions
  const value = {
    state,
    setBaseFile: (file) => dispatch({ type: actions.SET_BASE_FILE, payload: file }),
    setCompareFile: (file) => dispatch({ type: actions.SET_COMPARE_FILE, payload: file }),
    setComparisonId: (id) => {
      console.log('Dispatching SET_COMPARISON_ID with:', id);
      dispatch({ type: actions.SET_COMPARISON_ID, payload: id });
    },
    setComparisonResult: (result) => dispatch({ type: actions.SET_COMPARISON_RESULT, payload: result }),
    setLoading: (isLoading) => dispatch({ type: actions.SET_LOADING, payload: isLoading }),
    setError: (error) => dispatch({ type: actions.SET_ERROR, payload: error }),
    setSelectedPage: (page) => dispatch({ type: actions.SET_SELECTED_PAGE, payload: page }),
    setSelectedDifference: (diff) => dispatch({ type: actions.SET_SELECTED_DIFFERENCE, payload: diff }),
    setDocumentPairs: (pairs) => dispatch({ type: actions.SET_DOCUMENT_PAIRS, payload: pairs }),
    setSelectedDocumentPairIndex: (index) => dispatch({ type: actions.SET_SELECTED_DOCUMENT_PAIR_INDEX, payload: index }),
    updateFilters: (filters) => dispatch({ type: actions.UPDATE_FILTERS, payload: filters }),
    updateViewSettings: (settings) => dispatch({ type: actions.UPDATE_VIEW_SETTINGS, payload: settings }),
    updateProgress: (progress) => dispatch({ type: actions.UPDATE_PROGRESS, payload: progress }),
    resetComparison: () => dispatch({ type: actions.RESET_COMPARISON })
  };

  return (
    <ComparisonContext.Provider value={value}>
      {children}
    </ComparisonContext.Provider>
  );
};

// Custom hook to use the comparison context
export const useComparison = () => {
  const context = useContext(ComparisonContext);
  if (context === undefined) {
    throw new Error('useComparison must be used within a ComparisonProvider');
  }
  return context;
};
