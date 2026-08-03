import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useTrip } from '../context/TripContext';

const AttractionPage = () => {
  const navigate = useNavigate();
  const { addLocation } = useTrip();

  const handleAddToTrip = () => {
    addLocation('契迪龙寺');
    navigate('/route');
  };

  return (
    <div className="attraction-page">
      <div className="attraction-hero" style={{backgroundImage: 'url(https://images.unsplash.com/photo-1590059367468-61d0f5f725a3?w=1200&h=600&fit=crop)'}}>
        <div className="hero-overlay"></div>
        <div className="hero-content">
          <div className="container">
            <h1>契迪龙寺</h1>
            <p>Wat Chedi Luang Varavihara</p>
            <div className="hero-actions" style={{marginTop: '20px', display: 'flex', gap: '16px'}}>
              <button className="btn-primary" onClick={handleAddToTrip}>添加到行程</button>
              <button className="btn-secondary">收藏景点</button>
            </div>
          </div>
        </div>
      </div>

      <div className="attraction-layout">
        <div className="attraction-main">
          <div className="info-card">
            <h2>景点介绍</h2>
            <div className="metarow" style={{margin: '16px 0'}}>
              <span className="chip star">★ 4.8 (4.2k 点评)</span>
              <span className="chip">古寺庙</span>
              <span className="chip">兰纳风格</span>
              <span className="chip">清迈古城</span>
            </div>
            <p className="desc-text">
              契迪龙寺又称大佛塔寺，始建于1411年，是清迈市内六大寺庙中最为著名的寺庙。寺内的大佛塔曾在1545年的一次大地震中受损，但依然宏伟壮丽，是古城的地标。塔的南侧有6个象头雕像，其中5个都是后来用水泥修复的仿制品，只有最右侧的一个是砖砌的真品。
            </p>
          </div>

          <div className="info-card">
            <h2>游客点评 · 128</h2>
            <div className="comments-list">
              <div className="cmt-item">
                <div className="cmt-avatar" style={{background: '#EF6E5B'}}>林</div>
                <div className="cmt-body">
                  <div className="cmt-meta">
                    <strong>林小舟</strong>
                    <span className="cmt-stars">★★★★★</span>
                  </div>
                  <p className="cmt-text">傍晚去人少，光线特别好，拍照绝了。残缺的美更有韵味。</p>
                </div>
              </div>
              <div className="cmt-item">
                <div className="cmt-avatar" style={{background: '#0E9E8E'}}>A</div>
                <div className="cmt-body">
                  <div className="cmt-meta">
                    <strong>Aiko</strong>
                    <span className="cmt-stars">★★★★☆</span>
                  </div>
                  <p className="cmt-text">进殿需脱鞋，记得穿长裤。建议请个向导讲解，历史很丰富。</p>
                </div>
              </div>
            </div>
            <button className="btn-outline" style={{width: '100%', marginTop: '20px'}}>＋ 写点评</button>
          </div>
        </div>

        <div className="attraction-sidebar">
          <div className="info-card">
            <h3>实用信息</h3>
            <ul className="details-list">
              <li><strong>开放时间：</strong> 08:00 - 17:00</li>
              <li><strong>门票价格：</strong> 40 泰铢</li>
              <li><strong>建议游玩：</strong> 1.5 - 2 小时</li>
              <li><strong>地址：</strong> 103 Road King Prajadhipok Phra Sing, Chiang Mai</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AttractionPage;
