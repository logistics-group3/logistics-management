export interface SignupInput {
  username: string
  password: string
  passwordConfirm: string
  name: string
  slackId: string
  affiliationType: 'COMPANY' | 'HUB'
  affiliationName: string
}
export function validateSignup(input: SignupInput) {
  const errors: Partial<Record<keyof SignupInput, string>> = {}
  if (!/^[a-z0-9]{4,10}$/.test(input.username))
    errors.username = '아이디는 소문자·숫자 4~10자로 입력해 주세요.'
  if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,15}$/.test(input.password))
    errors.password = '비밀번호는 8~15자이며 대문자·소문자·숫자·특수문자를 모두 포함해야 합니다.'
  if (input.password !== input.passwordConfirm)
    errors.passwordConfirm = '비밀번호가 일치하지 않습니다.'
  if (!input.name.trim() || input.name.length > 50) errors.name = '이름을 1~50자로 입력해 주세요.'
  if (!input.slackId.trim() || input.slackId.length > 50)
    errors.slackId = 'Slack 멤버 ID를 1~50자로 입력해 주세요.'
  if (!input.affiliationName.trim() || input.affiliationName.length > 100)
    errors.affiliationName = '소속명을 1~100자로 입력해 주세요.'
  return errors
}
