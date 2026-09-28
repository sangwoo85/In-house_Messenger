import { Icon } from '@/components/Icon'

export type FileTransferStatus = 'uploading' | 'processing' | 'sending' | 'failed'

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes.toLocaleString()} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function FileUploadProgress({ file, percent, uploadedBytes, status, error, onRetry, onCancel }: {
  file: File; percent: number; uploadedBytes: number; status: FileTransferStatus; error?: string
  onRetry: () => void; onCancel: () => void
}): JSX.Element {
  const label = status === 'uploading' ? '업로드 중' : status === 'processing' ? '파일 처리 중' : status === 'sending' ? '메시지 전송 중' : '전송 실패'
  const volume = `${formatSize(uploadedBytes)} / ${formatSize(file.size)}`

  return <div className={`messenger-upload ${status === 'failed' ? 'is-failed' : ''}`} data-upload-state={status}>
    <div className="messenger-upload-heading">
      <span className="messenger-upload-icon"><Icon name="file" /></span>
      <span className="messenger-upload-name" title={file.name}>{file.name}</span>
      <span className="messenger-upload-percent">{percent}%</span>
    </div>
    <div className="messenger-upload-track" role="progressbar" aria-label={`${file.name} 업로드 진행률`}
      aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent} aria-valuetext={`${percent}%, ${volume}, ${label}`}>
      <div className="messenger-upload-fill" style={{ width: `${percent}%` }} />
    </div>
    <div className="messenger-upload-details"><span>{volume}</span><span role="status">{label}</span></div>
    {status === 'failed' && <div className="messenger-upload-error">
      <p role="alert">{error ?? '파일을 전송하지 못했습니다. 다시 시도해 주세요.'}</p>
      <div className="messenger-upload-actions"><button type="button" onClick={onRetry}>재시도</button><button type="button" onClick={onCancel}>취소</button></div>
    </div>}
  </div>
}
