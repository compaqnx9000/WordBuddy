export type PodcastEpisode = {
  id: string
  title: string
  audioUrl: string
  summary?: string
}

export type PodcastShow = {
  id: string
  title: string
  host: string
  blurb: string
  accentLabel: string
  accentColor: string
  episodes: PodcastEpisode[]
}

/** 与 App 内 PodcastCatalog 同步的直播电台（PC 端静态列表） */
export const LIVE_RADIO_SHOWS: PodcastShow[] = [
  {
    id: 'npr-live',
    title: 'NPR News',
    host: 'NPR · Live Radio',
    blurb: '美式英语新闻电台 · 24 小时直播',
    accentLabel: '美式',
    accentColor: '#2e7dff',
    episodes: [
      {
        id: 'npr-live-now',
        title: 'LIVE · NPR News',
        audioUrl: 'https://npr-ice.streamguys1.com/live.mp3',
        summary: '直播中',
      },
    ],
  },
  {
    id: 'lbc-live',
    title: 'LBC',
    host: 'LBC UK · Live Radio',
    blurb: '英式谈话电台 · 新闻与访谈',
    accentLabel: '英式',
    accentColor: '#e53935',
    episodes: [
      {
        id: 'lbc-live-now',
        title: 'LIVE · LBC UK',
        audioUrl: 'https://media-ice.musicradio.com/LBCUK',
        summary: '直播中',
      },
    ],
  },
  {
    id: 'cri-live',
    title: 'China Plus',
    host: 'CRI · Live Radio',
    blurb: '中国国际广播英文台 · 24 小时直播',
    accentLabel: '国际英语',
    accentColor: '#c62828',
    episodes: [
      {
        id: 'cri-live-now',
        title: 'LIVE · China Plus Radio',
        audioUrl: 'https://sk.cri.cn/am846.m3u8',
        summary: '直播中',
      },
    ],
  },
]
