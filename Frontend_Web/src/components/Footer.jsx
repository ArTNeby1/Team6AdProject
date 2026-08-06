import React from 'react';

const Footer = () => {
  return (
    <footer className="web-footer">
      <div className="container">
        <div className="footer-grid">
          <div className="footer-info">
            <h3 className="web-brand">LoomyTrip</h3>
            <p>Explore every corner of the world and make travel simpler.</p>
          </div>
          <div className="footer-links">
            <h4>Product</h4>
            <ul>
              <li><a href="#">AI Itinerary Planning</a></li>
              <li><a href="#">Popular Destinations</a></li>
              <li><a href="#">Travel Community</a></li>
            </ul>
          </div>
          <div className="footer-links">
            <h4>Company</h4>
            <ul>
              <li><a href="#">About Us</a></li>
              <li><a href="#">Join Us</a></li>
              <li><a href="#">Contact Us</a></li>
            </ul>
          </div>
          <div className="footer-links">
            <h4>Support</h4>
            <ul>
              <li><a href="#">Help Center</a></li>
              <li><a href="#">Privacy Policy</a></li>
              <li><a href="#">Terms of Service</a></li>
            </ul>
          </div>
        </div>
        <div className="footer-bottom">
          <p>© 2024 LoomyTrip. All rights reserved.</p>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
