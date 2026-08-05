import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTrip } from '../context/TripContext';

const ImportPage = () => {
  const navigate = useNavigate();
  const { createNewTrip } = useTrip();

  // Basic state
  const [text, setText] = useState("清迈超全攻略｜3天2夜这样玩🌴\nDay1 契迪龙寺 → 塔佩门 → 尼曼路 → 周日夜市，寺庙控必去…");
  const [results, setResults] = useState([]);
  const [isParsing, setIsParsing] = useState(false);
  const [isFinished, setIsFinished] = useState(false);

  // Mock data for AI extraction
  const mockAIItems = [
    { id: 'ext-1', name: '契迪龙寺', status: 'ok', label: '已定位', selected: true },
    { id: 'ext-2', name: '塔佩门', status: 'ok', label: '已定位', selected: true },
    { id: 'ext-3', name: '尼曼路', status: 'ok', label: '已定位', selected: true },
    { id: 'ext-4', name: '周日夜市', status: 'warn', label: '需确认时间', selected: true },
  ];

  // Effect to handle incremental extraction animation
  useEffect(() => {
    if (!isParsing) return;

    let currentIndex = 0;
    const interval = setInterval(() => {
      if (currentIndex < mockAIItems.length) {
        setResults(prev => [...prev, { ...mockAIItems[currentIndex], id: Date.now() + currentIndex }]);
        currentIndex++;
      } else {
        clearInterval(interval);
        setIsParsing(false);
        setIsFinished(true);
      }
    }, 800);

    return () => clearInterval(interval);
  }, [isParsing]);

  // Handlers
  const handleStartParsing = (e) => {
    if (e) e.preventDefault();
    if (isParsing || !text.trim()) return;

    setResults([]);
    setIsFinished(false);
    setIsParsing(true);
  };

  const toggleItemSelection = (id) => {
    setResults(prev => prev.map(item =>
      item.id === id ? { ...item, selected: !item.selected } : item
    ));
  };

  const deleteItem = (id) => {
    setResults(prev => prev.filter(item => item.id !== id));
  };

  const updateItemName = (id, newName) => {
    setResults(prev => prev.map(item =>
      item.id === id ? { ...item, name: newName } : item
    ));
  };

  const handleConfirmImport = (e) => {
    if (e) e.preventDefault();

    const selectedItems = results.filter(r => r.selected);
    if (selectedItems.length === 0) {
      alert("请至少选择一个地点加入行程");
      return;
    }

    try {
      // Create a NEW trip instead of adding to active one
      const newTripId = createNewTrip(selectedItems.map(item => item.name));

      // Navigate to the new trip's detail page
      navigate(`/itinerary/${newTripId}`);
    } catch (error) {
      console.error("Import failed:", error);
      alert("添加行程失败，请刷新重试");
    }
  };

  const handleReset = () => {
    setResults([]);
    setIsParsing(false);
    setIsFinished(false);
  };

  return (
    <div className="import-page" style={{ minHeight: '80vh', paddingBottom: '100px' }}>
      <div className="import-container" style={{ maxWidth: '800px', margin: '0 auto' }}>
        <header className="page-header" style={{ textAlign: 'center', marginBottom: '40px' }}>
          <h1 style={{ fontSize: '32px', marginBottom: '12px' }}>导入游记</h1>
          <p style={{ color: 'var(--muted)', fontSize: '16px' }}>沿途 AI 将为您提取景点、定位并串联最优路线</p>
        </header>

        {/* Input Section */}
        <div className="paste-area" style={{
          background: '#fff', border: '2px dashed var(--jade)',
          borderRadius: '24px', padding: '24px', boxShadow: 'var(--shadow-sm)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px', color: 'var(--jade-deep)', fontWeight: 'bold' }}>
            <span>📕 粘贴攻略文本 (支持小红书、马蜂窝等)</span>
          </div>
          <textarea
            style={{
              width: '100%', minHeight: '160px', border: 'none',
              fontSize: '17px', lineHeight: '1.7', outline: 'none',
              resize: 'none', color: 'var(--ink)'
            }}
            placeholder="在这里粘贴游记链接或文本内容..."
            value={text}
            onChange={(e) => setText(e.target.value)}
            disabled={isParsing}
          />
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '16px' }}>
            <button
              type="button"
              className="btn-primary"
              onClick={handleStartParsing}
              disabled={isParsing || !text.trim()}
              style={{ padding: '12px 40px', fontSize: '16px' }}
            >
              {isParsing ? '🚀 正在智能提取景点...' : '✨ 开始解析'}
            </button>
          </div>
        </div>

        {/* Results Section */}
        {(results.length > 0 || isParsing) && (
          <div className="parsing-status" style={{ marginTop: '48px' }}>
            <div className="agent-box" style={{
              display: 'flex', alignItems: 'center', gap: '16px',
              background: 'var(--mint)', padding: '20px 24px', borderRadius: '16px',
              marginBottom: '32px', border: '1px solid var(--line-soft)'
            }}>
              <div style={{ width: '44px', height: '44px', background: 'var(--amber)', borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '24px' }}>🤖</div>
              <div className="agent-text">
                <strong style={{ color: 'var(--jade-deep)', fontSize: '18px' }}>Yántú AI Agent</strong>
                <p style={{ marginTop: '4px', color: 'var(--ink-70)' }}>
                  {isParsing ? '正在深度分析文本内容，请稍候...' : `已为您识别 ${results.length} 个地点。您可以进行删改或勾选。`}
                </p>
              </div>
            </div>

            <div className="extracted-list" style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {results.map((res) => (
                <div key={res.id} style={{
                  background: '#fff', border: `1px solid ${res.selected ? 'var(--line-soft)' : 'var(--line-soft)'}`,
                  padding: '16px 24px', borderRadius: '20px', display: 'flex',
                  alignItems: 'center', gap: '20px', transition: 'all 0.3s',
                  opacity: res.selected ? 1 : 0.5,
                  boxShadow: res.selected ? 'var(--shadow-sm)' : 'none'
                }}>
                  {/* Selection Checkbox */}
                  <div
                    onClick={() => toggleItemSelection(res.id)}
                    style={{
                      width: '28px', height: '28px', border: `2px solid ${res.selected ? 'var(--jade)' : 'var(--line)'}`,
                      borderRadius: '8px', cursor: 'pointer', display: 'flex',
                      alignItems: 'center', justifyContent: 'center', color: 'var(--jade)',
                      background: res.selected ? 'var(--mint)' : 'transparent',
                      fontSize: '18px', fontWeight: 'bold'
                    }}
                  >
                    {res.selected ? '✓' : ''}
                  </div>

                  {/* Icon */}
                  <div style={{ fontSize: '24px' }}>{res.status === 'ok' ? '📍' : '⚠️'}</div>

                  {/* Editable Name */}
                  <input
                    type="text"
                    value={res.name}
                    onChange={(e) => updateItemName(res.id, e.target.value)}
                    style={{
                      flex: 1, border: '1px solid transparent', background: 'transparent',
                      fontSize: '18px', fontWeight: '700', padding: '6px 10px',
                      borderRadius: '8px', color: 'var(--ink)'
                    }}
                    onFocus={(e) => e.target.style.border = '1px solid var(--jade)'}
                    onBlur={(e) => e.target.style.border = '1px solid transparent'}
                    title="点击修改景点名称"
                  />

                  {/* Status Tag */}
                  <span style={{
                    padding: '6px 16px', borderRadius: '99px', fontSize: '13px', fontWeight: '800',
                    background: res.status === 'ok' ? 'var(--mint)' : '#FCEFD6',
                    color: res.status === 'ok' ? 'var(--jade-deep)' : '#9a6410'
                  }}>{res.label}</span>

                  {/* Delete Button */}
                  <button
                    type="button"
                    onClick={() => deleteItem(res.id)}
                    style={{
                      border: 'none', background: 'var(--line-soft)', color: 'var(--muted)',
                      width: '28px', height: '28px', borderRadius: '50%', cursor: 'pointer',
                      fontSize: '20px', display: 'flex', alignItems: 'center', justifyContent: 'center'
                    }}
                    title="从本次解析中移除"
                  >×</button>
                </div>
              ))}
            </div>

            {/* Bottom Confirmation Actions */}
            {isFinished && (
              <div className="confirm-actions" style={{ marginTop: '56px', display: 'flex', justifyContent: 'center', gap: '20px' }}>
                <button type="button" className="btn-secondary" onClick={handleReset} style={{ padding: '14px 32px', borderRadius: '99px' }}>
                  重新解析
                </button>
                <button
                  type="button"
                  className="btn-primary"
                  onClick={handleConfirmImport}
                  style={{
                    padding: '14px 60px', borderRadius: '99px', fontSize: '18px',
                    boxShadow: '0 8px 20px rgba(14, 158, 142, 0.3)'
                  }}
                >
                  确认并将 {results.filter(r => r.selected).length} 个地点加入行程 ➔
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default ImportPage;
