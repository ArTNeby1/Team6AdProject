import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

const HomePage = () => {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');

  const allPlaces = ["新加坡滨海湾", "圣淘沙岛", "牛车水 (Chinatown)", "小印度", "乌节路", "新加坡环球影城", "清迈古城", "契迪龙寺", "素贴山", "宁曼路"];
  const searchResults = allPlaces.filter(p => p.toLowerCase().includes(query.toLowerCase()) && query !== '');

  return (
    <div className="home-page">
      <section className="hero">
        <div className="container">
          <h1>你好，准备去哪儿？</h1>
          <p>把你的游记链接粘贴在这里，沿途 AI 自动为你生成智能行程。</p>
          <div className="hero-search-box">
            <div className="search-input">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="11" cy="11" r="8"></circle>
                <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
              </svg>
              <input
                type="text"
                placeholder="搜索目的地、游记或景点..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </div>
            <button className="btn-primary">智能规划</button>

            {searchResults.length > 0 && (
              <div className="search-dropdown" style={{
                position: 'absolute', top: '100%', left: 0, right: 0,
                background: '#fff', border: '1px solid var(--line)',
                borderRadius: '8px', marginTop: '8px', zIndex: 10,
                boxShadow: 'var(--shadow-sm)'
              }}>
                {searchResults.map((res, i) => (
                  <div key={i} className="search-result-item" style={{
                    padding: '12px 16px', borderBottom: i === searchResults.length - 1 ? 'none' : '1px solid var(--line-soft)',
                    cursor: 'pointer'
                  }} onClick={() => { setQuery(res); navigate('/attraction'); }}>
                    {res}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </section>

      <section className="featured-trips">
        <div className="section-title">
          <h2>正在进行的行程</h2>
          <a href="#" className="link-more">查看全部</a>
        </div>
        <div className="destination-grid">
          <div className="destination-card" onClick={() => navigate('/route')}>
            <div className="dest-img" style={{backgroundImage: 'linear-gradient(rgba(0,0,0,0),rgba(0,0,0,0.6)), url(https://images.unsplash.com/photo-1528181304800-2f1738b9cdc1?w=600&h=400&fit=crop)'}}>
              <h3>清迈深度文化之旅</h3>
            </div>
            <div className="dest-info">
              <p>3天 · 12个景点 · 24日出发</p>
              <div className="progress-bar">
                <div className="progress" style={{width: '60%'}}></div>
              </div>
              <a className="go-link">继续规划 ➔</a>
            </div>
          </div>
        </div>
      </section>

      <section className="popular-destinations" style={{marginTop: '60px'}}>
        <div className="section-title">
          <h2>热门目的地</h2>
        </div>
        <div className="destination-grid">
          {[
            { name: "曼谷", count: "8.2k", img: "https://images.unsplash.com/photo-1552465011-b4e21bf6e79a" },
            { name: "普吉岛", count: "5.4k", img: "https://images.unsplash.com/photo-1537996194471-e657df975ab4" },
            { name: "京都", count: "12.1k", img: "https://images.unsplash.com/photo-1513415277900-a62401e19be4" }
          ].map((dest, i) => (
            <div key={i} className="destination-card" onClick={() => navigate('/attraction')}>
              <div className="dest-img" style={{backgroundImage: `linear-gradient(rgba(0,0,0,0),rgba(0,0,0,0.6)), url(${dest.img}?w=600&h=400&fit=crop)`}}>
                <h3>{dest.name}</h3>
              </div>
              <div className="dest-info">
                <p>{dest.count} 人收藏</p>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
};

export default HomePage;
