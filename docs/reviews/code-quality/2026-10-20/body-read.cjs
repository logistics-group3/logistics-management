const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const assert = require('node:assert/strict');
const repo = process.argv[2];
const ts = require(path.join(repo, 'frontend/node_modules/typescript'));
const source = fs.readFileSync(path.join(repo, 'frontend/src/shared/api.ts'), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
const mod = new Module(path.join(repo, 'frontend/src/shared/api.audit.cjs'));
mod.filename = path.join(repo, 'frontend/src/shared/api.audit.cjs');
mod.paths = Module._nodeModulePaths(path.join(repo, 'frontend'));
mod._compile(compiled, mod.filename);
global.fetch = async () => new Response(new ReadableStream({
  start(controller) { controller.error(new TypeError('response body disconnected')); }
}), { status: 201 });
(async () => {
  try {
    await mod.exports.api('/companies', { method: 'POST', body: { name: 'audit' } });
    assert.fail('body failure must reject');
  } catch (error) {
    const isApiError = error instanceof mod.exports.ApiError;
    const uncertain = isApiError && (error.status === 0 || error.status >= 500);
    console.log('AUDIT_BODY_ERROR_TYPE=' + error.constructor.name);
    console.log('AUDIT_CREATE_FORM_MARKS_UNCERTAIN=' + uncertain);
    assert.equal(isApiError, true, '본문 수신 실패도 ApiError(0)로 정규화해야 폼이 중복 등록을 막는다');
  }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
