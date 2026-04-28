import { create } from 'zustand'

export type ViewMode = 'users' | 'chat' | 'notifications'

interface UiState {
  viewMode: ViewMode
  setViewMode: (viewMode: ViewMode) => void
}

export const useUiStore = create<UiState>((set) => ({
  viewMode: 'users',
  setViewMode: (viewMode) => set({ viewMode })
}))
