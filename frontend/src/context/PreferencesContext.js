import React, { createContext, useContext, useState, useEffect } from 'react';

// Default preferences
const defaultPreferences = {
  theme: 'light', // 'light', 'dark', 'system'
  differenceColors: {
    text: '#FF5252',     // Red for text differences
    font: '#FF9800',     // Orange for font differences
    image: '#2196F3',    // Blue for image differences
    style: '#4CAF50',    // Green for style differences
    metadata: '#9C27B0'  // Purple for metadata differences
  },
  defaultDifferenceThreshold: 'normal', // 'strict', 'normal', 'relaxed'
  autoNavigatePages: true,
  showRulers: false,
  showThumbnails: true,
  thumbnailSize: 'medium', // 'small', 'medium', 'large'
  textComparisonMethod: 'smart', // 'exact', 'smart', 'fuzzy'
  interfaceLayout: 'sideBySide', // 'sideBySide', 'overlay', 'stacked'
  showPageBorders: true,
  useAnimations: true
};

// Create the context
const PreferencesContext = createContext();

// Create the context provider component
export const PreferencesProvider = ({ children }) => {
  // Try to load saved preferences from local storage
  const loadSavedPreferences = () => {
    try {
      const saved = localStorage.getItem('pdfComparePreferences');
      return saved ? { ...defaultPreferences, ...JSON.parse(saved) } : defaultPreferences;
    } catch (error) {
      console.error('Failed to load preferences:', error);
      return defaultPreferences;
    }
  };

  const [preferences, setPreferences] = useState(loadSavedPreferences);

  // Save preferences to local storage when they change
  useEffect(() => {
    try {
      localStorage.setItem('pdfComparePreferences', JSON.stringify(preferences));
    } catch (error) {
      console.error('Failed to save preferences:', error);
    }
  }, [preferences]);

  // Function to update preferences
  const updatePreferences = (newPreferences) => {
    setPreferences(prev => ({
      ...prev,
      ...newPreferences
    }));
  };

  // Function to reset preferences to defaults
  const resetPreferences = () => {
    setPreferences(defaultPreferences);
  };

  // Update theme based on system preference if set to 'system'
  useEffect(() => {
    if (preferences.theme === 'system') {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      
      const applyTheme = (event) => {
        document.documentElement.setAttribute(
          'data-theme', 
          event.matches ? 'dark' : 'light'
        );
      };
      
      applyTheme(mediaQuery);
      mediaQuery.addEventListener('change', applyTheme);
      
      return () => mediaQuery.removeEventListener('change', applyTheme);
    } else {
      document.documentElement.setAttribute('data-theme', preferences.theme);
    }
  }, [preferences.theme]);

  const value = {
    preferences,
    updatePreferences,
    resetPreferences
  };

  return (
    <PreferencesContext.Provider value={value}>
      {children}
    </PreferencesContext.Provider>
  );
};

// Custom hook to use the preferences context
export const usePreferences = () => {
  const context = useContext(PreferencesContext);
  if (context === undefined) {
    throw new Error('usePreferences must be used within a PreferencesProvider');
  }
  return context;
};