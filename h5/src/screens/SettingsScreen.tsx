import { FONT_SCALES, type FontSizeOption } from '../types'
import type { HotWordsApi } from '../store/useHotWords'

const FONT_OPTIONS: FontSizeOption[] = ['Small', 'Normal', 'Large', 'ExtraLarge']
const FONT_LABEL: Record<FontSizeOption, string> = {
  Small: '小',
  Normal: '标准',
  Large: '大',
  ExtraLarge: '特大',
}

export function SettingsScreen({
  api,
  onBack,
}: {
  api: HotWordsApi
  onBack: () => void
}) {
  const s = api.ui.settings
  const currentFont =
    FONT_OPTIONS.find((o) => Math.abs(FONT_SCALES[o] - s.fontScale) < 0.01) ?? 'Normal'

  return (
    <div className="screen">
      <div className="card-top">
        <button type="button" className="icon-btn" onClick={onBack} aria-label="返回">
          ←
        </button>
        <strong>设置</strong>
        <span style={{ width: 36 }} />
      </div>
      <div className="settings-panel screen-scroll">
        <div className="settings-group">
          <h3>显示名</h3>
          <input
            className="search-input"
            style={{ width: '100%', borderRadius: 12 }}
            value={s.displayName}
            onChange={(e) => api.updateSettings((x) => ({ ...x, displayName: e.target.value }))}
          />
        </div>

        <div className="settings-group">
          <h3>主题</h3>
          <div className="seg">
            <button
              type="button"
              className={s.appTheme === 'Light' ? 'active' : ''}
              onClick={() => api.updateSettings((x) => ({ ...x, appTheme: 'Light' }))}
            >
              明亮
            </button>
            <button
              type="button"
              className={s.appTheme === 'Dark' ? 'active' : ''}
              onClick={() => api.updateSettings((x) => ({ ...x, appTheme: 'Dark' }))}
            >
              暗黑
            </button>
          </div>
        </div>

        <div className="settings-group">
          <h3>字体</h3>
          <div className="seg">
            {FONT_OPTIONS.map((o) => (
              <button
                key={o}
                type="button"
                className={currentFont === o ? 'active' : ''}
                onClick={() => api.updateSettings((x) => ({ ...x, fontScale: FONT_SCALES[o] }))}
              >
                {FONT_LABEL[o]}
              </button>
            ))}
          </div>
        </div>

        <div className="settings-group">
          <h3>发音</h3>
          <div className="seg">
            <button
              type="button"
              className={s.accent === 'UK' ? 'active' : ''}
              onClick={() => api.updateSettings((x) => ({ ...x, accent: 'UK' }))}
            >
              英音
            </button>
            <button
              type="button"
              className={s.accent === 'US' ? 'active' : ''}
              onClick={() => api.updateSettings((x) => ({ ...x, accent: 'US' }))}
            >
              美音
            </button>
          </div>
        </div>

        <div className="menu-row" style={{ marginTop: 18 }}>
          <span className="label">翻页自动朗读</span>
          <button
            type="button"
            className={`toggle${s.speakOnPageChange ? ' on' : ''}`}
            onClick={() =>
              api.updateSettings((x) => ({ ...x, speakOnPageChange: !x.speakOnPageChange }))
            }
            aria-label="翻页自动朗读"
          />
        </div>
        <div className="menu-row">
          <span className="label">卡片循环</span>
          <button
            type="button"
            className={`toggle${s.loop ? ' on' : ''}`}
            onClick={() => api.updateSettings((x) => ({ ...x, loop: !x.loop }))}
            aria-label="卡片循环"
          />
        </div>

        <div className="settings-group">
          <h3>自动播放间隔（秒）</h3>
          <div className="seg">
            {[1.5, 2.5, 4, 6].map((sec) => (
              <button
                key={sec}
                type="button"
                className={s.autoPlayIntervalMs === sec * 1000 ? 'active' : ''}
                onClick={() =>
                  api.updateSettings((x) => ({ ...x, autoPlayIntervalMs: sec * 1000 }))
                }
              >
                {sec}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
