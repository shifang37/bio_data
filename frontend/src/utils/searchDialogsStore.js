import { reactive } from 'vue'

// 全局搜索对话框状态管理
export const searchDialogsState = reactive({
  // 搜索对话框列表
  searchDialogs: [],
  
  // 下一个对话框ID
  nextDialogId: 1,
  
  // 设置搜索对话框数据
  setSearchDialogs(dialogs, nextId = 1) {
    this.searchDialogs = dialogs
    this.nextDialogId = nextId
    this.saveToStorage()
  },
  
  // 添加新的搜索对话框
  addSearchDialog(searchValue, result) {
    const newDialog = {
      id: this.nextDialogId++,
      searchValue: searchValue,
      result: result,
      visible: true,
      minimized: false
    }
    this.searchDialogs.push(newDialog)
    this.saveToStorage()
    return newDialog
  },
  
  // 最小化对话框
  minimizeDialog(dialogId) {
    const dialog = this.searchDialogs.find(d => d.id === dialogId)
    if (dialog) {
      dialog.minimized = true
      dialog.visible = false
      this.saveToStorage()
    }
  },
  
  // 恢复对话框
  restoreDialog(dialogId) {
    const dialog = this.searchDialogs.find(d => d.id === dialogId)
    if (dialog) {
      dialog.minimized = false
      dialog.visible = true
      this.saveToStorage()
    }
  },
  
  // 关闭对话框
  closeDialog(dialogId) {
    const index = this.searchDialogs.findIndex(d => d.id === dialogId)
    if (index !== -1) {
      this.searchDialogs.splice(index, 1)
      this.saveToStorage()
    }
  },
  
  // 清空单个搜索
  clearSingleSearch(dialogId) {
    this.closeDialog(dialogId)
  },
  
  // 清空所有搜索
  clearAllSearches() {
    this.searchDialogs = []
    this.saveToStorage()
  },
  
  // 清空特定数据库的搜索结果
  clearSearchesByDatabase(databaseName) {
    if (!databaseName) return
    
    // 只保留不是来自指定数据库的搜索弹窗
    this.searchDialogs = this.searchDialogs.filter(dialog => {
      // 获取搜索结果的数据库来源
      const dialogDataSource = dialog.result?.dataSource 
      
      // 如果没有数据库信息，则保留（可能是老的搜索结果）
      if (!dialogDataSource) {
        console.log('保留没有数据库信息的搜索结果:', dialog.searchValue)
        return true
      }
      
      // 只有当数据库来源不同时才保留
      const shouldKeep = dialogDataSource !== databaseName
      if (!shouldKeep) {
        console.log('清空来自数据库', databaseName, '的搜索结果:', dialog.searchValue)
      }
      return shouldKeep
    })
    
    this.saveToStorage()
  },
  
  // 保存到localStorage
  saveToStorage() {
    try {
      const dataToSave = {
        searchDialogs: this.searchDialogs,
        nextDialogId: this.nextDialogId,
        timestamp: Date.now()
      }
      localStorage.setItem('searchDialogsData', JSON.stringify(dataToSave))
    } catch (error) {
      console.error('保存搜索对话框数据到localStorage失败:', error)
    }
  },
  
  // 从localStorage恢复数据
  loadFromStorage() {
    try {
      const savedData = localStorage.getItem('searchDialogsData')
      if (savedData) {
        const data = JSON.parse(savedData)
        
        // 检查数据是否过期（24小时）
        const now = Date.now()
        const dataAge = now - (data.timestamp || 0)
        const maxAge = 24 * 60 * 60 * 1000 // 24小时
        
        if (dataAge < maxAge) {
          this.searchDialogs = data.searchDialogs || []
          this.nextDialogId = data.nextDialogId || 1
          return true
        } else {
          // 数据过期，清除
          this.clearStorage()
        }
      }
    } catch (error) {
      console.error('从localStorage恢复搜索对话框数据失败:', error)
      this.clearStorage()
    }
    return false
  },
  
  // 清除localStorage
  clearStorage() {
    try {
      localStorage.removeItem('searchDialogsData')
    } catch (error) {
      console.error('清除localStorage中的搜索对话框数据失败:', error)
    }
  }
})

export default searchDialogsState
