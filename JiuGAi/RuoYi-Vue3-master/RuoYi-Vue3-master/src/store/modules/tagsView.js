import cache from '@/plugins/cache'
import useSettingsStore from '@/store/modules/settings'

const PERSIST_KEY = 'tags-view-visited'

function isPersistEnabled() {
  return useSettingsStore().tagsViewPersist
}

function saveVisitedViews(views) {
  if (!isPersistEnabled()) return
  const toSave = views
    .filter((view) => !(view.meta && view.meta.affix))
    .map((view) => ({
      path: view.path,
      fullPath: view.fullPath,
      name: view.name,
      title: view.title,
      query: view.query,
      meta: view.meta
    }))
  cache.local.setJSON(PERSIST_KEY, toSave)
}

function loadVisitedViews() {
  return cache.local.getJSON(PERSIST_KEY) || []
}

function clearVisitedViews() {
  cache.local.remove(PERSIST_KEY)
}

const useTagsViewStore = defineStore('tags-view', {
  state: () => ({
    visitedViews: [],
    cachedViews: []
  }),
  actions: {
    addView(view) {
      this.addVisitedView(view)
      this.addCachedView(view)
    },
    addVisitedView(view) {
      if (this.visitedViews.some((item) => item.path === view.path)) return
      this.visitedViews.push(
        Object.assign({}, view, {
          title: view.meta.title || 'no-name'
        })
      )
      saveVisitedViews(this.visitedViews)
    },
    addAffixView(view) {
      if (this.visitedViews.some((item) => item.path === view.path)) return
      this.visitedViews.unshift(
        Object.assign({}, view, {
          title: view.meta.title || 'no-name'
        })
      )
    },
    addCachedView(view) {
      if (this.cachedViews.includes(view.name)) return
      if (!view.meta.noCache) {
        this.cachedViews.push(view.name)
      }
    },
    delView(view) {
      return new Promise((resolve) => {
        this.delVisitedView(view)
        this.delCachedView(view)
        resolve({
          visitedViews: [...this.visitedViews],
          cachedViews: [...this.cachedViews]
        })
      })
    },
    delVisitedView(view) {
      return new Promise((resolve) => {
        for (const [index, item] of this.visitedViews.entries()) {
          if (item.path === view.path) {
            this.visitedViews.splice(index, 1)
            break
          }
        }
        saveVisitedViews(this.visitedViews)
        resolve([...this.visitedViews])
      })
    },
    delCachedView(view) {
      return new Promise((resolve) => {
        const index = this.cachedViews.indexOf(view.name)
        if (index > -1) {
          this.cachedViews.splice(index, 1)
        }
        resolve([...this.cachedViews])
      })
    },
    delOthersViews(view) {
      return new Promise((resolve) => {
        this.delOthersVisitedViews(view)
        this.delOthersCachedViews(view)
        resolve({
          visitedViews: [...this.visitedViews],
          cachedViews: [...this.cachedViews]
        })
      })
    },
    delOthersVisitedViews(view) {
      return new Promise((resolve) => {
        this.visitedViews = this.visitedViews.filter((item) => item.meta.affix || item.path === view.path)
        saveVisitedViews(this.visitedViews)
        resolve([...this.visitedViews])
      })
    },
    delOthersCachedViews(view) {
      return new Promise((resolve) => {
        const index = this.cachedViews.indexOf(view.name)
        this.cachedViews = index > -1 ? this.cachedViews.slice(index, index + 1) : []
        resolve([...this.cachedViews])
      })
    },
    delAllViews() {
      return new Promise((resolve) => {
        this.delAllVisitedViews()
        this.delAllCachedViews()
        resolve({
          visitedViews: [...this.visitedViews],
          cachedViews: [...this.cachedViews]
        })
      })
    },
    delAllVisitedViews() {
      return new Promise((resolve) => {
        const affixTags = this.visitedViews.filter((tag) => tag.meta.affix)
        this.visitedViews = affixTags
        clearVisitedViews()
        resolve([...this.visitedViews])
      })
    },
    delAllCachedViews() {
      return new Promise((resolve) => {
        this.cachedViews = []
        resolve([...this.cachedViews])
      })
    },
    updateVisitedView(view) {
      for (let item of this.visitedViews) {
        if (item.path === view.path) {
          item = Object.assign(item, view)
          break
        }
      }
    },
    delRightTags(view) {
      return new Promise((resolve) => {
        const index = this.visitedViews.findIndex((item) => item.path === view.path)
        if (index === -1) return
        this.visitedViews = this.visitedViews.filter((item, idx) => {
          if (idx <= index || (item.meta && item.meta.affix)) {
            return true
          }
          const cacheIndex = this.cachedViews.indexOf(item.name)
          if (cacheIndex > -1) {
            this.cachedViews.splice(cacheIndex, 1)
          }
          return false
        })
        saveVisitedViews(this.visitedViews)
        resolve([...this.visitedViews])
      })
    },
    delLeftTags(view) {
      return new Promise((resolve) => {
        const index = this.visitedViews.findIndex((item) => item.path === view.path)
        if (index === -1) return
        this.visitedViews = this.visitedViews.filter((item, idx) => {
          if (idx >= index || (item.meta && item.meta.affix)) {
            return true
          }
          const cacheIndex = this.cachedViews.indexOf(item.name)
          if (cacheIndex > -1) {
            this.cachedViews.splice(cacheIndex, 1)
          }
          return false
        })
        saveVisitedViews(this.visitedViews)
        resolve([...this.visitedViews])
      })
    },
    loadPersistedViews() {
      const views = loadVisitedViews()
      views.forEach((view) => {
        this.addVisitedView(view)
      })
    }
  }
})

export default useTagsViewStore
