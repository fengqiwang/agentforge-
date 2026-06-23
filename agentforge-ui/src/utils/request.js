import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

request.interceptors.response.use(
  response => response.data,
  error => {
    console.error('请求失败：', error.response?.data || error.message)
    return Promise.reject(error)
  }
)

export default request
