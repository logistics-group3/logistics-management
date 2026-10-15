import * as DialogPrimitive from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import { useRef } from 'react'
import type { ReactNode } from 'react'
export function Dialog({
  open,
  onClose,
  title,
  description,
  children,
  sheet = false,
}: {
  open: boolean
  onClose: () => void
  title: string
  description: string
  children: ReactNode
  sheet?: boolean
}) {
  const opener = useRef<HTMLElement | null>(null)
  return (
    <DialogPrimitive.Root
      open={open}
      onOpenChange={(value) => {
        if (!value) onClose()
      }}
    >
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="dialog-overlay" />
        <DialogPrimitive.Content
          className={sheet ? 'dialog-content sheet' : 'dialog-content'}
          onOpenAutoFocus={() => {
            opener.current =
              document.activeElement instanceof HTMLElement ? document.activeElement : null
          }}
          onCloseAutoFocus={(event) => {
            event.preventDefault()
            if (sheet) document.getElementById('page-title')?.focus()
            else if (opener.current?.isConnected) opener.current.focus()
            else document.querySelector<HTMLElement>('[role="dialog"] button')?.focus()
          }}
        >
          <header className="dialog-header">
            <div>
              <DialogPrimitive.Title>{title}</DialogPrimitive.Title>
              <DialogPrimitive.Description>{description}</DialogPrimitive.Description>
            </div>
            <DialogPrimitive.Close className="icon-button" aria-label="닫기">
              <X size={20} />
            </DialogPrimitive.Close>
          </header>
          <div className="dialog-body">{children}</div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
