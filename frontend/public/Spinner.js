import React from 'react';
import './Spinner.css';

const Spinner = ({ size = 'medium' }) => {
  return (
    <div className={`spinner ${size}`}>
      <div className="spinner-inner"></div>
    </div>
  );
};

export default Spinner;