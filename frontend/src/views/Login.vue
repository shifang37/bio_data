<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h2>{{ isAdmin ? '管理员登录' : '用户登录' }}</h2>
        <div class="login-tabs">
          <button 
            class="tab-btn" 
            :class="{ active: !isAdmin }" 
            @click="switchTab(false)"
          >
            用户登录
          </button>
          <button 
            class="tab-btn" 
            :class="{ active: isAdmin }" 
            @click="switchTab(true)"
          >
            管理员登录
          </button>
        </div>
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label for="username">用户名:</label>
          <input
            id="username"
            v-model="loginForm.username"
            type="text"
            class="form-input"
            placeholder="请输入用户名"
            required
          />
        </div>

        <div class="form-group">
          <label for="password">密码:</label>
          <input
            id="password"
            v-model="loginForm.password"
            type="password"
            class="form-input"
            placeholder="请输入密码"
            required
          />
        </div>

        <button 
          type="submit" 
          class="login-btn"
          :disabled="loading"
        >
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <div class="login-footer">
        <!-- 只有普通用户才显示注册链接，管理员账户由系统添加 -->
        <p v-if="!isAdmin">
          还没有账号？
          <a href="#" @click="showRegister = true">立即注册</a>
        </p>
        <!-- 忘记密码选项仅对普通用户显示 -->
        <p v-if="!isAdmin">
          <a href="#" @click="showForgotPassword = true">忘记密码？</a>
        </p>
        <p>
          <a href="#" @click="testConnection">测试数据库连接</a>
        </p>
      </div>

      <!-- 错误提示 -->
      <div v-if="error" class="error-message">
        {{ error }}
      </div>

      <!-- 成功提示 -->
      <div v-if="success" class="success-message">
        {{ success }}
      </div>
    </div>

    <!-- 注册模态框 - 只支持普通用户注册 -->
    <div v-if="showRegister" class="modal-overlay" @click="closeModal">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>用户注册</h3>
          <button class="close-btn" @click="closeModal">&times;</button>
        </div>
        
        <form @submit.prevent="handleRegister">
          <div class="form-group">
            <label for="reg-username">用户名:</label>
            <input
              id="reg-username"
              v-model="registerForm.username"
              type="text"
              class="form-input"
              placeholder="请输入用户名"
              required
            />
          </div>

          <div class="form-group">
            <label for="reg-password">密码:</label>
            <input
              id="reg-password"
              v-model="registerForm.password"
              type="password"
              class="form-input"
              placeholder="请输入密码"
              required
            />
          </div>

          <div class="form-group">
            <label for="reg-email">邮箱:</label>
            <input
              id="reg-email"
              v-model="registerForm.email"
              type="email"
              class="form-input"
              placeholder="请输入邮箱地址"
              required
            />
            <small class="form-hint">用于接收账户激活邮件</small>
          </div>

          <div class="form-group">
            <label for="reg-captcha">验证码:</label>
            <div class="captcha-container">
              <input
                id="reg-captcha"
                v-model="registerForm.captchaInput"
                type="text"
                class="form-input captcha-input"
                placeholder="请输入验证码"
                required
              />
              <div class="captcha-image" @click="refreshCaptcha">
                <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
                <div v-else class="captcha-loading">加载中...</div>
              </div>
            </div>
            <small class="form-hint">点击图片刷新验证码</small>
          </div>

          <div class="form-group">
            <label for="reg-permission">权限:</label>
            <select
              id="reg-permission"
              v-model="registerForm.permission"
              class="form-input"
              disabled
            >
              <option value="user">普通用户</option>
            </select>
            <small class="form-hint">注：管理员账户由系统管理员添加</small>
          </div>

          <div class="modal-footer">
            <button type="button" class="cancel-btn" @click="closeModal">
              取消
            </button>
            <button type="submit" class="submit-btn" :disabled="loading">
              {{ loading ? '注册中...' : '注册' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 忘记密码模态框 -->
    <div v-if="showForgotPassword" class="modal-overlay" @click="closeForgotPasswordModal">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>忘记密码</h3>
          <button class="close-btn" @click="closeForgotPasswordModal">&times;</button>
        </div>
        
        <!-- 第一步：验证用户名 -->
        <div v-if="forgotPasswordStep === 1">
          <p class="step-description">请输入您的用户名，我们将验证账号是否存在</p>
          <form @submit.prevent="checkUser">
            <div class="form-group">
              <label for="forgot-username">用户名:</label>
              <input
                id="forgot-username"
                v-model="forgotPasswordForm.username"
                type="text"
                class="form-input"
                placeholder="请输入用户名"
                required
              />
            </div>

            <div class="modal-footer">
              <button type="button" class="cancel-btn" @click="closeForgotPasswordModal">
                取消
              </button>
              <button type="submit" class="submit-btn" :disabled="loading">
                {{ loading ? '验证中...' : '下一步' }}
              </button>
            </div>
          </form>
        </div>

        <!-- 第二步：设置新密码 -->
        <div v-if="forgotPasswordStep === 2">
          <p class="step-description">用户名验证成功，请设置新密码</p>
          <form @submit.prevent="resetPassword">
            <div class="form-group">
              <label for="new-password">新密码:</label>
              <input
                id="new-password"
                v-model="forgotPasswordForm.newPassword"
                type="password"
                class="form-input"
                placeholder="请输入新密码（至少6位）"
                required
                minlength="6"
              />
            </div>

            <div class="form-group">
              <label for="confirm-password">确认密码:</label>
              <input
                id="confirm-password"
                v-model="forgotPasswordForm.confirmPassword"
                type="password"
                class="form-input"
                placeholder="请再次输入新密码"
                required
              />
            </div>

            <div class="modal-footer">
              <button type="button" class="cancel-btn" @click="forgotPasswordStep = 1">
                上一步
              </button>
              <button type="submit" class="submit-btn" :disabled="loading">
                {{ loading ? '重置中...' : '重置密码' }}
              </button>
            </div>
          </form>
        </div>

        <!-- 错误提示 -->
        <div v-if="forgotPasswordError" class="error-message">
          {{ forgotPasswordError }}
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import api, { userState } from '@/utils/api'
import { knowledgeGraphState } from '@/utils/knowledgeGraphStore'
import { searchDialogsState } from '@/utils/searchDialogsStore'

export default {
  name: 'Login',
  created() {
    // 将 userState 添加到 Vue 实例
    this.$userState = userState
  },
  data() {
    return {
      isAdmin: false,
      showRegister: false,
      showForgotPassword: false,
      forgotPasswordStep: 1, // 1: 验证用户名, 2: 设置新密码
      loading: false,
      error: '',
      success: '',
      forgotPasswordError: '',
      loginForm: {
        username: '',
        password: ''
      },
      registerForm: {
        username: '',
        password: '',
        email: '',
        captchaInput: '',
        permission: 'user'
      },
      captchaImage: null,
      captchaToken: null,
      forgotPasswordForm: {
        username: '',
        newPassword: '',
        confirmPassword: ''
      }
    }
  },
  watch: {
    // 监听注册对话框显示状态，自动加载验证码
    showRegister(newVal) {
      if (newVal) {
        this.loadCaptcha()
      }
    }
  },
  
  methods: {
    switchTab(isAdmin) {
      this.isAdmin = isAdmin
      this.error = ''
      this.success = ''
      this.registerForm.permission = isAdmin ? 'admin' : 'user'
    },

    async handleLogin() {
      this.loading = true
      this.error = ''
      this.success = ''

      try {
        const endpoint = this.isAdmin ? '/api/auth/login/admin' : '/api/auth/login/user'
        const response = await api.post(endpoint, {
          username: this.loginForm.username,
          password: this.loginForm.password
        })

        if (response.data.success) {
          this.success = response.data.message
          
          try {
            // 获取用户权限信息
            const permissionResponse = await api.get('/api/auth/permission', {
              params: {
                userId: response.data.userId,
                userType: response.data.userType
              }
            })

            // 设置用户状态
            const userInfo = {
              userId: response.data.userId,
              username: response.data.username,
              userType: response.data.userType,
              permission: response.data.permission,
              canAccessLogin: permissionResponse.data.canAccessLogin || false,
              canModifyLogin: permissionResponse.data.canModifyLogin || false
            }

            // 使用 userState 设置用户信息
            this.$userState.setUserInfo(userInfo)
            
            // 存储用户信息到 localStorage（向后兼容）
            localStorage.setItem('user', JSON.stringify(userInfo))

          } catch (permissionError) {
            console.warn('获取权限信息失败，使用默认权限:', permissionError)
            
            // 如果权限查询失败，使用默认权限
            const userInfo = {
              userId: response.data.userId,
              username: response.data.username,
              userType: response.data.userType,
              permission: response.data.permission,
              canAccessLogin: response.data.userType === 'admin',
              canModifyLogin: response.data.userType === 'admin'
            }

            this.$userState.setUserInfo(userInfo)
            localStorage.setItem('user', JSON.stringify(userInfo))
          }

          // 延迟跳转到主页面
          setTimeout(() => {
            this.$router.push('/tables')
          }, 1500)
        } else {
          this.error = response.data.message
        }
      } catch (error) {
        this.error = error.response?.data?.message || '登录失败，请检查网络连接'
        console.error('登录错误:', error)
      } finally {
        this.loading = false
      }
    },

    async handleRegister() {
      this.loading = true
      this.error = ''
      this.success = ''

      try {
        // 验证邮箱格式
        const emailRegex = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
        if (!emailRegex.test(this.registerForm.email)) {
          this.error = '请输入正确的邮箱格式'
          this.loading = false
          return
        }

        // 验证验证码是否填写
        if (!this.registerForm.captchaInput || !this.captchaToken) {
          this.error = '请输入验证码'
          this.loading = false
          return
        }

        // 新版本注册接口，包含邮箱和验证码
        const registerData = {
          username: this.registerForm.username,
          password: this.registerForm.password,
          email: this.registerForm.email,
          captchaToken: this.captchaToken,
          captchaInput: this.registerForm.captchaInput
        }
        
        const response = await api.post('/api/auth/register/user', registerData)

        if (response.data.success) {
          this.success = response.data.message
          this.showRegister = false
          this.resetRegisterForm()
          
          // 如果需要激活，显示激活提示
          if (response.data.needActivation) {
            this.success = '注册成功！请检查邮箱并点击激活链接完成账户激活。'
          }
        } else {
          this.error = response.data.message
          // 如果验证码错误，刷新验证码
          if (response.data.message.includes('验证码')) {
            await this.loadCaptcha()
          }
        }
      } catch (error) {
        this.error = error.response?.data?.message || '注册失败，请检查网络连接'
        console.error('注册错误:', error)
        // 注册失败时也刷新验证码
        await this.loadCaptcha()
      } finally {
        this.loading = false
      }
    },

    async testConnection() {
      try {
        const response = await api.get('/api/auth/test-connection')
        if (response.data.connected) {
          this.success = response.data.message
        } else {
          this.error = response.data.message
        }
      } catch (error) {
        this.error = '连接测试失败'
        console.error('连接测试错误:', error)
      }
    },

    closeModal() {
      this.showRegister = false
      this.error = ''
      this.resetRegisterForm()
    },
    
    resetRegisterForm() {
      this.registerForm = {
        username: '',
        password: '',
        email: '',
        captchaInput: '',
        permission: 'user'
      }
      this.captchaImage = null
      this.captchaToken = null
    },
    
    // 加载验证码
    async loadCaptcha() {
      try {
        const response = await api.get('/api/auth/captcha')
        if (response.data.success) {
          this.captchaImage = response.data.captchaImage
          this.captchaToken = response.data.captchaToken
        } else {
          console.error('获取验证码失败:', response.data.message)
        }
      } catch (error) {
        console.error('获取验证码异常:', error)
      }
    },
    
    // 刷新验证码
    async refreshCaptcha() {
      this.registerForm.captchaInput = ''
      await this.loadCaptcha()
    },

    // 忘记密码相关方法
    async checkUser() {
      this.loading = true
      this.forgotPasswordError = ''

      try {
        const response = await api.post('/api/auth/check-user', {
          username: this.forgotPasswordForm.username
        })

        if (response.data.success && response.data.exists) {
          this.forgotPasswordStep = 2
          this.forgotPasswordError = ''
        } else {
          this.forgotPasswordError = response.data.message || '用户不存在'
        }
      } catch (error) {
        this.forgotPasswordError = error.response?.data?.message || '验证失败，请检查网络连接'
        console.error('检查用户错误:', error)
      } finally {
        this.loading = false
      }
    },

    async resetPassword() {
      // 验证密码
      if (this.forgotPasswordForm.newPassword !== this.forgotPasswordForm.confirmPassword) {
        this.forgotPasswordError = '两次输入的密码不一致'
        return
      }

      if (this.forgotPasswordForm.newPassword.length < 6) {
        this.forgotPasswordError = '密码长度至少6位'
        return
      }

      this.loading = true
      this.forgotPasswordError = ''

      try {
        const response = await api.post('/api/auth/reset-password', {
          username: this.forgotPasswordForm.username,
          newPassword: this.forgotPasswordForm.newPassword
        })

        if (response.data.success) {
          this.success = response.data.message
          this.closeForgotPasswordModal()
          
          // 可以选择自动填入登录表单
          this.loginForm.username = this.forgotPasswordForm.username
          this.loginForm.password = ''
          
          // 显示成功消息一段时间后清除
          setTimeout(() => {
            this.success = ''
          }, 3000)
        } else {
          this.forgotPasswordError = response.data.message
        }
      } catch (error) {
        this.forgotPasswordError = error.response?.data?.message || '密码重置失败，请检查网络连接'
        console.error('重置密码错误:', error)
      } finally {
        this.loading = false
      }
    },

    closeForgotPasswordModal() {
      this.showForgotPassword = false
      this.forgotPasswordStep = 1
      this.forgotPasswordError = ''
      this.forgotPasswordForm = {
        username: '',
        newPassword: '',
        confirmPassword: ''
      }
    }
  },

  mounted() {
    // 清除之前的登录信息
    this.$userState.clearUserInfo()
    
    // 清除知识图谱数据
    knowledgeGraphState.clearData()
    
    // 清除搜索对话框数据
    searchDialogsState.clearAllSearches()
    
    // 清除所有用户相关的sessionStorage数据
    const clearAllUserData = () => {
      try {
        const keys = Object.keys(sessionStorage)
        keys.forEach(key => {
          if (key.includes('knowledgeGraphData_') || key.includes('searchDialogsData_')) {
            sessionStorage.removeItem(key)
          }
        })
      } catch (error) {
        console.error('清除用户数据失败:', error)
      }
    }
    clearAllUserData()
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.login-card {
  background: white;
  border-radius: 10px;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.1);
  padding: 40px;
  width: 100%;
  max-width: 400px;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h2 {
  color: #333;
  margin-bottom: 20px;
}

.login-tabs {
  display: flex;
  border-radius: 5px;
  overflow: hidden;
  border: 1px solid #ddd;
}

.tab-btn {
  flex: 1;
  padding: 10px;
  border: none;
  background: #f8f9fa;
  color: #666;
  cursor: pointer;
  transition: all 0.3s;
}

.tab-btn.active {
  background: #667eea;
  color: white;
}

.tab-btn:hover {
  background: #e9ecef;
}

.tab-btn.active:hover {
  background: #5a6fd8;
}

.login-form {
  margin-bottom: 20px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 5px;
  color: #333;
  font-weight: 500;
}

.form-input {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 5px;
  font-size: 16px;
  transition: border-color 0.3s;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
}

.form-hint {
  display: block;
  margin-top: 5px;
  font-size: 12px;
  color: #666;
  font-style: italic;
}

.login-btn {
  width: 100%;
  padding: 12px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 5px;
  font-size: 16px;
  cursor: pointer;
  transition: background 0.3s;
}

.login-btn:hover:not(:disabled) {
  background: #5a6fd8;
}

.login-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.login-footer {
  text-align: center;
  margin-top: 20px;
}

.login-footer p {
  margin-bottom: 10px;
  color: #666;
}

.login-footer a {
  color: #667eea;
  text-decoration: none;
}

.login-footer a:hover {
  text-decoration: underline;
}

.error-message {
  background: #f8d7da;
  color: #721c24;
  padding: 10px;
  border-radius: 5px;
  margin-top: 15px;
  text-align: center;
}

.success-message {
  background: #d4edda;
  color: #155724;
  padding: 10px;
  border-radius: 5px;
  margin-top: 15px;
  text-align: center;
}

/* 模态框样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 10px;
  padding: 30px;
  width: 90%;
  max-width: 400px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.modal-header h3 {
  margin: 0;
  color: #333;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #999;
}

.close-btn:hover {
  color: #333;
}

.modal-footer {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.cancel-btn,
.submit-btn {
  flex: 1;
  padding: 10px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
}

.cancel-btn {
  background: #6c757d;
  color: white;
}

.cancel-btn:hover {
  background: #5a6268;
}

.submit-btn {
  background: #667eea;
  color: white;
}

.submit-btn:hover:not(:disabled) {
  background: #5a6fd8;
}

.submit-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

/* 忘记密码相关样式 */
.step-description {
  color: #666;
  font-size: 14px;
  margin-bottom: 20px;
  line-height: 1.4;
  text-align: center;
  background: #f8f9fa;
  padding: 10px;
  border-radius: 5px;
  border-left: 4px solid #667eea;
}
.captcha-container {
  display: flex;
  gap: 10px;
  align-items: center;
}

.captcha-input {
  flex: 1;
}

.captcha-image {
  width: 120px;
  height: 40px;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f9f9f9;
  transition: background-color 0.2s;
}

.captcha-image:hover {
  background-color: #f0f0f0;
}

.captcha-image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.captcha-loading {
  font-size: 12px;
  color: #666;
}
</style> 