import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getProfileImage, profileImagePath } from '@/features/users/profile.api'
import { useAuthStore } from '@/stores/auth.store'

interface AvatarProps {
  name: string
  imageUrl?: string | null
  group?: boolean
  small?: boolean
}

/** 보호된 프로필 사진을 인증 후 표시한다. 실패하면 이니셜을 사용하고 Blob URL은 해제한다. */
export function Avatar({ name, imageUrl, group = false, small = false }: AvatarProps): JSX.Element {
  const epoch = useAuthStore(state => state.epoch)
  const signedIn = useAuthStore(state => Boolean(state.accessToken))
  const path = group ? null : profileImagePath(imageUrl)
  const [preview, setPreview] = useState<{ path: string; url: string; epoch: number } | null>(null)
  const photo = useQuery({
    queryKey: ['profile-image', epoch, path],
    queryFn: () => getProfileImage(path!),
    enabled: Boolean(path) && signedIn,
    staleTime: 5 * 60 * 1000,
    retry: false
  })

  useEffect(() => {
    if (!photo.data || !path) { setPreview(null); return }
    const url = URL.createObjectURL(photo.data)
    setPreview({ path, url, epoch })
    return () => URL.revokeObjectURL(url)
  }, [photo.data, path, epoch])

  const source = signedIn && preview?.path === path && preview.epoch === epoch ? preview.url : null
  return <span className={`messenger-avatar ${group ? 'is-group' : ''} ${small ? 'is-small' : ''}`}>
    {source ? <img src={source} alt={name} onError={() => setPreview(null)} /> : group ? '#' : name.slice(0, 2)}
  </span>
}
