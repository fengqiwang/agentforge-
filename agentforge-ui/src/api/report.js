import request from '@/utils/request'

/** 从问题构建报表 */
export function buildReport(question) {
  return request.post('/report/build', { question })
}

/**
 * 从SQL构建报表
 * 注意：后端 ReportController.buildFromSql 读取的键是 sqlText，必须对齐，
 *       否则会被判空返回 400 "SQL不能为空"。
 */
export function buildReportFromSql(name, sql) {
  return request.post('/report/build-sql', { name, sqlText: sql })
}

/** 保存报表 */
export function saveReport(config) {
  return request.post('/report/save', config)
}

/** 报表列表 */
export function listReports() {
  return request.get('/report/list')
}

/** 删除报表 */
export function deleteReport(id) {
  return request.delete(`/report/${id}`)
}

/** 通过分享令牌查看报表（含最新数据） */
export function viewReport(shareToken) {
  return request.get(`/report/view/${shareToken}`)
}

/** 参数化执行SQL（筛选联动） */
export function executeParamSql(sql, params) {
  return request.post('/report/execute', { sql, params })
}

/** SQL生成 */
export function generateSql(question) {
  return request.post('/sql/generate', { question })
}

/** SQL生成+执行 */
export function executeSql(question) {
  return request.post('/sql/execute', { question })
}
