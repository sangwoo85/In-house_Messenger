import { create } from 'zustand'

type ViewMode = 'chat' | 'notifications'

interface UiState {
  viewMode: ViewMode
  setViewMode: (viewMode: ViewMode) => void
}

export const useUiStore = create<UiState>((set) => ({
  viewMode: 'chat',
  setViewMode: (viewMode) => set({ viewMode })
}))

