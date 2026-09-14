import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('cc_token') || '',
    name: localStorage.getItem('cc_name') || '',
    role: Number(localStorage.getItem('cc_role') || 1)
  }),
  actions: {
    setLogin(data) {
      this.token = data.token
      this.name = data.name
      this.role = data.role
      localStorage.setItem('cc_token', data.token)
      localStorage.setItem('cc_name', data.name)
      localStorage.setItem('cc_role', data.role)
    },
    logout() {
      this.token = ''
      this.name = ''
      localStorage.removeItem('cc_token')
      localStorage.removeItem('cc_name')
      localStorage.removeItem('cc_role')
    }
  }
})
