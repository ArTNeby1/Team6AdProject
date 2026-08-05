import React from 'react';

const Footer = () => {
  return (
    <footer className="web-footer">
      <div className="container">
        <div className="footer-grid">
          <div className="footer-info">
            <h3 className="web-brand"><span className="cn">沿途</span> Yántú</h3>
            <p>探索世界的每一个角落，让旅行变得更简单。</p>
          </div>
          <div className="footer-links">
            <h4>产品</h4>
            <ul>
              <li><a href="#">AI 行程规划</a></li>
              <li><a href="#">热门目的地</a></li>
              <li><a href="#">旅行社区</a></li>
            </ul>
          </div>
          <div className="footer-links">
            <h4>公司</h4>
            <ul>
              <li><a href="#">关于我们</a></li>
              <li><a href="#">加入我们</a></li>
              <li><a href="#">联系我们</a></li>
            </ul>
          </div>
          <div className="footer-links">
            <h4>支持</h4>
            <ul>
              <li><a href="#">帮助中心</a></li>
              <li><a href="#">隐私政策</a></li>
              <li><a href="#">服务条款</a></li>
            </ul>
          </div>
        </div>
        <div className="footer-bottom">
          <p>© 2024 Yántú 沿途. 保留所有权利。</p>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
