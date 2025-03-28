import React, { useState } from 'react';
import { usePreferences } from '../../context/PreferencesContext';
import './ComparisonSettings.css';

const ComparisonSettings = ({ settings, onSettingChange }) => {
  const { preferences } = usePreferences();
  const [expanded, setExpanded] = useState(false);

  const handleToggleExpand = () => {
    setExpanded(!expanded);
  };

  return (
    <div className={`comparison-settings ${expanded ? 'expanded' : 'collapsed'}`}>
      <div className="settings-header" onClick={handleToggleExpand}>
        <h3>Comparison Settings</h3>
        <button className="toggle-button">
          {expanded ? (
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z" />
            </svg>
          ) : (
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z" />
            </svg>
          )}
        </button>
      </div>

      {expanded && (
        <div className="settings-content">
          <div className="settings-group">
            <h4>Text Comparison</h4>
            <div className="setting-item">
              <label htmlFor="textComparisonMethod">Comparison Method:</label>
              <select 
                id="textComparisonMethod" 
                value={settings.textComparisonMethod}
                onChange={(e) => onSettingChange('textComparisonMethod', e.target.value)}
              >
                <option value="exact">Exact Match</option>
                <option value="smart">Smart (Ignore whitespace)</option>
                <option value="fuzzy">Fuzzy (Tolerance for minor differences)</option>
              </select>
            </div>

            <div className="setting-item">
              <label htmlFor="differenceThreshold">Difference Threshold:</label>
              <select 
                id="differenceThreshold" 
                value={settings.differenceThreshold}
                onChange={(e) => onSettingChange('differenceThreshold', e.target.value)}
              >
                <option value="strict">Strict (Report all differences)</option>
                <option value="normal">Normal</option>
                <option value="relaxed">Relaxed (Ignore minor differences)</option>
              </select>
            </div>
          </div>

          <div className="settings-group">
            <h4>Visual Comparison</h4>
            <div className="setting-item checkbox">
              <input 
                type="checkbox" 
                id="ignoreColors" 
                checked={settings.ignoreColors}
                onChange={(e) => onSettingChange('ignoreColors', e.target.checked)}
              />
              <label htmlFor="ignoreColors">Ignore Color Differences</label>
            </div>

            <div className="setting-item checkbox">
              <input 
                type="checkbox" 
                id="ignorePositioning" 
                checked={settings.ignorePositioning}
                onChange={(e) => onSettingChange('ignorePositioning', e.target.checked)}
              />
              <label htmlFor="ignorePositioning">Ignore Minor Position Differences</label>
            </div>
          </div>

          <div className="settings-group">
            <h4>Elements to Compare</h4>
            <div className="setting-item checkbox">
              <input 
                type="checkbox" 
                id="compareAnnotations" 
                checked={settings.compareAnnotations}
                onChange={(e) => onSettingChange('compareAnnotations', e.target.checked)}
              />
              <label htmlFor="compareAnnotations">Compare Annotations</label>
            </div>

            <div className="setting-item checkbox">
              <input 
                type="checkbox" 
                id="compareBookmarks" 
                checked={settings.compareBookmarks}
                onChange={(e) => onSettingChange('compareBookmarks', e.target.checked)}
              />
              <label htmlFor="compareBookmarks">Compare Bookmarks</label>
            </div>

            <div className="setting-item checkbox">
              <input 
                type="checkbox" 
                id="compareMetadata" 
                checked={settings.compareMetadata}
                onChange={(e) => onSettingChange('compareMetadata', e.target.checked)}
              />
              <label htmlFor="compareMetadata">Compare Metadata</label>
            </div>
          </div>

          <div className="settings-actions">
            <button 
              className="reset-button"
              onClick={() => {
                onSettingChange('textComparisonMethod', preferences.textComparisonMethod);
                onSettingChange('differenceThreshold', preferences.defaultDifferenceThreshold);
                onSettingChange('ignoreColors', false);
                onSettingChange('ignorePositioning', false);
                onSettingChange('compareAnnotations', true);
                onSettingChange('compareBookmarks', true);
                onSettingChange('compareMetadata', true);
              }}
            >
              Reset to Defaults
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default ComparisonSettings;