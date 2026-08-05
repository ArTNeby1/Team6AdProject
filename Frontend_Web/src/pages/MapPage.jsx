import React from 'react';

const MapPage = () => {
  return (
    <div className="map-page">
      <header className="page-header" style={{marginBottom: '32px'}}>
        <h1>探索地图</h1>
        <p>可视化查看你的行程路线与周边推荐。</p>
      </header>

      <div className="map-container">
        <div className="map-view">
          <svg viewBox="0 0 800 600" style={{width: '100%', height: '100%', display: 'block', background: '#f0ede5'}}>
            {/* Mock Map Background */}
            <path d="M0,100 L800,100 M0,300 L800,300 M0,500 L800,500 M200,0 L200,600 M400,0 L400,600 M600,0 L600,600" stroke="#e0d8c8" strokeWidth="1" />
            <path d="M100,150 Q400,50 700,150 T700,450" fill="none" stroke="#e0d8c8" strokeWidth="40" strokeLinecap="round" opacity="0.5" />

            {/* Route Line */}
            <path d="M200,200 L350,250 L300,400" fill="none" stroke="#0E9E8E" strokeWidth="4" strokeDasharray="10 5" />

            {/* Markers */}
            <g transform="translate(200, 200)">
              <circle r="12" fill="#0E9E8E" />
              <text y="30" textAnchor="middle" style={{fontSize: '14px', fontWeight: 700}}>契迪龙寺</text>
            </g>
            <g transform="translate(350, 250)">
              <circle r="12" fill="#0E9E8E" />
              <text y="30" textAnchor="middle" style={{fontSize: '14px', fontWeight: 700}}>帕辛寺</text>
            </g>
            <g transform="translate(300, 400)">
              <circle r="12" fill="#F0A038" />
              <text y="30" textAnchor="middle" style={{fontSize: '14px', fontWeight: 700}}>宁曼路午餐</text>
            </g>
          </svg>

          <div className="map-controls">
            <div className="control-group">
              <button className="active">路线顺序</button>
              <button>周边景点</button>
              <button>交通热力</button>
            </div>
          </div>
        </div>

        <div className="map-sidebar">
          <h3>Day 1 行程概览</h3>
          <div className="route-meta-web">
            <div className="meta-item">
              <strong>3 个地点</strong>
              <span>地点总数</span>
            </div>
            <div className="meta-item">
              <strong>5.4km</strong>
              <span>预估里程</span>
            </div>
            <div className="meta-item">
              <strong>45min</strong>
              <span>交通耗时</span>
            </div>
          </div>

          <div className="place-list-web" style={{marginTop: '32px'}}>
            <div className="place-item-web">
              <div className="num">1</div>
              <div className="info">
                <h4>契迪龙寺</h4>
                <p>09:30 - 11:00</p>
              </div>
            </div>
            <div className="place-item-web">
              <div className="num">2</div>
              <div className="info">
                <h4>帕辛寺</h4>
                <p>11:12 - 12:10</p>
              </div>
            </div>
            <div className="place-item-web active">
              <div className="num">3</div>
              <div className="info">
                <h4>宁曼路午餐</h4>
                <p>12:30 - 14:00</p>
              </div>
            </div>
          </div>

          <div className="sidebar-action" style={{marginTop: 'auto', paddingTop: '40px'}}>
            <button className="btn-primary" style={{width: '100%'}}>同步到手机导航</button>
          </div>
        </div>
      </div>

    </div>
  );
};

export default MapPage;
