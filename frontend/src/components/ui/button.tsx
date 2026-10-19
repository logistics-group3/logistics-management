import type { ButtonHTMLAttributes } from 'react'
import { Slot } from '@radix-ui/react-slot'
import { cva } from 'class-variance-authority'
import { clsx } from 'clsx'
const styles = cva('btn', {
  variants: {
    variant: {
      primary: 'btn-primary',
      secondary: 'btn-secondary',
      ghost: 'btn-ghost',
      danger: 'btn-danger',
    },
  },
  defaultVariants: { variant: 'primary' },
})
export function Button({
  variant = 'primary',
  asChild = false,
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  asChild?: boolean
}) {
  const Component = asChild ? Slot : 'button'
  return <Component className={clsx(styles({ variant }), className)} {...props} />
}
