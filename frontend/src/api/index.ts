import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.data?.message) {
      return Promise.reject(new Error(error.response.data.message))
    }
    return Promise.reject(error)
  },
)

export default api
