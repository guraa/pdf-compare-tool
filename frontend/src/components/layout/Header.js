import React, { useState } from 'react';
import { usePreferences } from '../../context/PreferencesContext';
import './Header.css';

const Header = () => {
  const { preferences, updatePreferences } = usePreferences();
  const [settingsOpen, setSettingsOpen] = useState(false);

  const toggleTheme = () => {
    const newTheme = preferences.theme === 'light' ? 'dark' : 'light';
    updatePreferences({ theme: newTheme });
  };

  const toggleSettings = () => {
    setSettingsOpen(!settingsOpen);
  };

  return (
    <header className="app-header">
      <div className="header-container">
        <div className="logo">
          <svg className="logo-icon" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M7 3C5.9 3 5 3.9 5 5v14c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2H7zm0 2h10v14H7V5zm2 2v2h6V7H9zm0 4v2h6v-2H9zm0 4v2h6v-2H9z" />
          </svg>
          <span className="logo-text">Doctechtive</span>
        </div>
        
        <div className="header-actions">
          <button 
            className="theme-toggle" 
            onClick={toggleTheme}
            title={`Switch to ${preferences.theme === 'light' ? 'dark' : 'light'} theme`}
          >
            {preferences.theme === 'light' ? (
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 3a9 9 0 1 0 9 9c0-.46-.04-.92-.1-1.36a5.389 5.389 0 0 1-4.4 2.26 5.403 5.403 0 0 1-3.14-9.8c-.44-.06-.9-.1-1.36-.1z" />
              </svg>
            ) : (
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58a.996.996 0 0 0-1.41 0 .996.996 0 0 0 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41L5.99 4.58zm12.37 12.37a.996.996 0 0 0-1.41 0 .996.996 0 0 0 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0a.996.996 0 0 0 0-1.41l-1.06-1.06zm1.06-10.96a.996.996 0 0 0 0-1.41.996.996 0 0 0-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06zM7.05 18.36a.996.996 0 0 0 0-1.41.996.996 0 0 0-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06z" />
              </svg>
            )}
          </button>
          
          <button 
            className="settings-button" 
            onClick={toggleSettings}
            title="Settings"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z" />
            </svg>
          </button>
        </div>
      </div>
      
      {settingsOpen && (
        <div className="settings-panel">
          <div className="settings-content">
            <h3>Application Settings</h3>
            
            <div className="settings-section">
              <h4>Appearance</h4>
              
              <div className="setting-item">
                <label htmlFor="themeSelect">Theme</label>
                <select 
                  id="themeSelect"
                  value={preferences.theme}
                  onChange={(e) => updatePreferences({ theme: e.target.value })}
                >
                  <option value="light">Light</option>
                  <option value="dark">Dark</option>
                  <option value="system">System Default</option>
                </select>
              </div>
              
              <div className="setting-item">
                <label htmlFor="interfaceLayout">Interface Layout</label>
                <select 
                  id="interfaceLayout"
                  value={preferences.interfaceLayout}
                  onChange={(e) => updatePreferences({ interfaceLayout: e.target.value })}
                >
                  <option value="sideBySide">Side by Side</option>
                  <option value="overlay">Overlay</option>
                  <option value="stacked">Stacked</option>
                </select>
              </div>
              
              <div className="setting-item checkbox">
                <input 
                  type="checkbox" 
                  id="showPageBorders" 
                  checked={preferences.showPageBorders}
                  onChange={(e) => updatePreferences({ showPageBorders: e.target.checked })}
                />
                <label htmlFor="showPageBorders">Show Page Borders</label>
              </div>
              
              <div className="setting-item checkbox">
                <input 
                  type="checkbox" 
                  id="useAnimations" 
                  checked={preferences.useAnimations}
                  onChange={(e) => updatePreferences({ useAnimations: e.target.checked })}
                />
                <label htmlFor="useAnimations">Use Animations</label>
              </div>
            </div>
            
            <div className="settings-section">
              <h4>Comparison</h4>
              
              <div className="setting-item">
                <label htmlFor="differenceThreshold">Default Difference Threshold</label>
                <select 
                  id="differenceThreshold"
                  value={preferences.defaultDifferenceThreshold}
                  onChange={(e) => updatePreferences({ defaultDifferenceThreshold: e.target.value })}
                >
                  <option value="strict">Strict (Report all differences)</option>
                  <option value="normal">Normal</option>
                  <option value="relaxed">Relaxed (Ignore minor differences)</option>
                </select>
              </div>
              
              <div className="setting-item">
                <label htmlFor="textComparisonMethod">Text Comparison Method</label>
                <select 
                  id="textComparisonMethod"
                  value={preferences.textComparisonMethod}
                  onChange={(e) => updatePreferences({ textComparisonMethod: e.target.value })}
                >
                  <option value="exact">Exact Match</option>
                  <option value="smart">Smart (Ignore whitespace)</option>
                  <option value="fuzzy">Fuzzy (Tolerance for minor differences)</option>
                </select>
              </div>
              
              <div className="setting-item checkbox">
                <input 
                  type="checkbox" 
                  id="autoNavigatePages" 
                  checked={preferences.autoNavigatePages}
                  onChange={(e) => updatePreferences({ autoNavigatePages: e.target.checked })}
                />
                <label htmlFor="autoNavigatePages">Auto-navigate to next page with differences</label>
              </div>
              
              <div className="setting-item checkbox">
                <input 
                  type="checkbox" 
                  id="showThumbnails" 
                  checked={preferences.showThumbnails}
                  onChange={(e) => updatePreferences({ showThumbnails: e.target.checked })}
                />
                <label htmlFor="showThumbnails">Show Page Thumbnails</label>
              </div>
            </div>
            
            <div className="settings-section">
              <h4>Highlighting Colors</h4>
              
              <div className="color-settings">
                <div className="color-item">
                  <label htmlFor="textColor">Text</label>
                  <input 
                    type="color" 
                    id="textColor" 
                    value={preferences.differenceColors.text}
                    onChange={(e) => updatePreferences({ 
                      differenceColors: {
                        ...preferences.differenceColors,
                        text: e.target.value
                      }
                    })}
                  />
                </div>
                
                <div className="color-item">
                  <label htmlFor="imageColor">Images</label>
                  <input 
                    type="color" 
                    id="imageColor" 
                    value={preferences.differenceColors.image}
                    onChange={(e) => updatePreferences({ 
                      differenceColors: {
                        ...preferences.differenceColors,
                        image: e.target.value
                      }
                    })}
                  />
                </div>
                
                <div className="color-item">
                  <label htmlFor="fontColor">Fonts</label>
                  <input 
                    type="color" 
                    id="fontColor" 
                    value={preferences.differenceColors.font}
                    onChange={(e) => updatePreferences({ 
                      differenceColors: {
                        ...preferences.differenceColors,
                        font: e.target.value
                      }
                    })}
                  />
                </div>
                
                <div className="color-item">
                  <label htmlFor="styleColor">Styles</label>
                  <input 
                    type="color" 
                    id="styleColor" 
                    value={preferences.differenceColors.style}
                    onChange={(e) => updatePreferences({ 
                      differenceColors: {
                        ...preferences.differenceColors,
                        style: e.target.value
                      }
                    })}
                  />
                </div>
              </div>
            </div>
            
            <div className="settings-actions">
              <button 
                className="reset-button" 
                onClick={() => updatePreferences(undefined)}
              >
                Reset to Defaults
              </button>
              
              <button 
                className="close-button" 
                onClick={toggleSettings}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </header>
  );
};

export default Header;