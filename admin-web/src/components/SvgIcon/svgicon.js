import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Back,
  Check,
  CircleCheck,
  CircleClose,
  Close,
  CopyDocument,
  DataAnalysis,
  Delete,
  DocumentAdd,
  DocumentCopy,
  Edit,
  Files,
  FirstAidKit,
  FullScreen,
  Hide,
  Key,
  Loading,
  Menu,
  Notebook,
  Plus,
  Postcard,
  Refresh,
  RefreshRight,
  Right,
  Search,
  Tickets,
  Timer,
  Upload,
  UploadFilled,
  User,
  View
} from '@element-plus/icons-vue'

const components = {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Back,
  Check,
  CircleCheck,
  CircleClose,
  Close,
  CopyDocument,
  DataAnalysis,
  Delete,
  DocumentAdd,
  DocumentCopy,
  Edit,
  Files,
  FirstAidKit,
  FullScreen,
  Hide,
  Key,
  Loading,
  Menu,
  Notebook,
  Plus,
  Postcard,
  Refresh,
  RefreshRight,
  Right,
  Search,
  Tickets,
  Timer,
  Upload,
  UploadFilled,
  User,
  View
}

export default {
  install: (app) => {
    for (const key in components) {
      const componentConfig = components[key]
      app.component(componentConfig.name, componentConfig)
    }
  }
}
