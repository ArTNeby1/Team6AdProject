import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTrip } from '../context/TripContext';

const ImportPage = () => {
  const navigate = useNavigate();
  const { addLocation } = useTrip();
  const [text, setText] = useState("清迈超全攻略｜3天2夜这样玩🌴\nDay1 契迪龙寺 → 塔佩门 → 尼曼路 → 周日夜市，寺庙控必去…");
  const [isExtracting, setIsExtracting] = useState(false);
  const [results, setResults] = useState([]);

  const handleRunAI = () => {
    setIsExtracting(true);
    setResults([]);

    // Simulating AI extraction steps
    setTimeout(() => {
      const items = [
        { name: '契迪龙寺', status: 'ok', label: '已定位' },
        { name: '塔佩门', status: 'ok', label: '已定位' },
        { name: '尼曼路', status: 'ok', label: '已定位' },
        { name: '周日夜市', status: 'warn', label: '需确认时间' },
      ];

      let i = 0;
      const interval = setInterval(() => {
        if (i < items.length) {
          setResults(prev => [...prev, items[i]]);
          i++;
        } else {
          clearInterval(interval);
          setIsExtracting(false);
        }
      }, 600);
    }, 1000);
  };

  const handleConfirm = () => {
    results.forEach(res => {
      if (res.status === 'ok') addLocation(res.name);
    });
    navigate('/route');
  };

  return (
    <div className="import-page">
      <div className="import-container">
        <header className="page-header" style={{textAlign: 'center', marginBottom: '40px'}}>
          <h1>导入游记</h1>
          <p>粘贴小红书、马蜂窝或其他平台的游记链接，沿途 AI 将为你解析并生成路线。</p>
        </header>

        <div className="paste-area">
          <div className="link-source">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
              <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
            </svg>
            <span>支持小红书 / 马蜂窝 / 携程链接</span>
          </div>
          <textarea
            placeholder="在这里粘贴游记链接或文本内容..."
            value={text}
            onChange={(e) => setText(e.target.value)}
          ></textarea>
          <div style={{textAlign: 'right', marginTop: '16px'}}>
            <button
              className="btn-primary"
              style={{fontSize: '18px', padding: '12px 40px'}}
              onClick={handleRunAI}
              disabled={isExtracting || !text}
            >
              {isExtracting ? '🚀 正在解析...' : '✨ 开始解析'}
            </button>
          </div>
        </div>

        {(isExtracting || results.length > 0) && (
          <div className="parsing-status">
            <div className="agent-box">
              <div className="agent-avatar">A</div>
              <div className="agent-text">
                <strong>Yántú AI Agent</strong> {isExtracting ? '正在分析文本...' : `已识别 ${results.length} 个地点`}
              </div>
            </div>

            <div className="extracted-list">
              {results.map((res, i) => (
                <div key={i} className="extract-item">
                  <div className="point-icon">{res.status === 'ok' ? '📍' : '⚠️'}</div>
                  <div className="point-name">{res.name}</div>
                  <span className={res.status === 'ok' ? 'tag-ok' : 'tag-warn'}>{res.label}</span>
                </div>
              ))}
            </div>

            {!isExtracting && results.length > 0 && (
              <div style={{marginTop: '32px', textAlign: 'center'}}>
                <button className="btn-primary" onClick={handleConfirm}>确认并生成行程</button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default ImportPage;
