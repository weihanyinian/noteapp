package com.example.mymind

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.example.mymind.databinding.ActivityMainBinding
import com.example.mymind.ui.mindmap.MindMapListFragment
import com.example.mymind.ui.note.NoteListFragment
import com.example.mymind.ui.home.HomeDashboardFragment
import com.example.mymind.ui.simple.SimplePageFragment
import com.example.mymind.ui.trash.TrashActivity

/**
 * 主界面：
 * - 轻量导航：手机底部导航；平板侧边栏（NavigationRail）
 * - Fragment 容器：切换不同模块页面
 *
 * 语言切换依赖 AppCompat 应用级 Locale；设置页切换后会重建 Activity 以刷新资源。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentCheckedNavId: Int = R.id.nav_recent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupMainNavigation()

        if (savedInstanceState == null) {
            if (!handleDestinationIntent(intent)) {
                selectDestination(R.id.nav_recent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDestinationIntent(intent)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.topAppBar)
        binding.topAppBar.menu.findItem(R.id.nav_trash_top)?.isVisible = false

        binding.topAppBar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.nav_settings -> {
                    openSimplePage(SimplePageFragment.PAGE_SETTINGS)
                    true
                }
                R.id.nav_help -> {
                    openSimplePage(SimplePageFragment.PAGE_HELP)
                    true
                }
                R.id.nav_about -> {
                    openSimplePage(SimplePageFragment.PAGE_ABOUT)
                    true
                }
                else -> false
            }
        }

        binding.drawerView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_trash -> {
                    startActivity(TrashActivity.createIntent(this, getDestinationString(currentCheckedNavId)))
                    true
                }
                else -> false
            }.also {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
    }

    private fun getDestinationString(navId: Int): String {
        return when (navId) {
            R.id.nav_my_mindmaps -> DEST_MINDMAPS
            R.id.nav_notes -> DEST_NOTES
            else -> DEST_RECENT
        }
    }

    private fun setupMainNavigation() {
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        binding.navigationRail.visibility = if (isTablet) android.view.View.VISIBLE else android.view.View.GONE
        binding.bottomNavigation.visibility = if (isTablet) android.view.View.GONE else android.view.View.VISIBLE

        binding.bottomNavigation.menu.findItem(R.id.nav_trash)?.isVisible = false
        binding.navigationRail.menu.findItem(R.id.nav_trash)?.isVisible = false

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            selectDestination(item.itemId)
            true
        }
        binding.navigationRail.setOnItemSelectedListener { item ->
            selectDestination(item.itemId)
            true
        }
    }

    private fun selectDestination(navId: Int) {
        when (navId) {
            R.id.nav_recent -> openHome()
            R.id.nav_my_mindmaps -> openMindMapList()
            R.id.nav_notes -> openNoteList()
        }
    }

    private fun openMindMapList() {
        val title = getString(R.string.nav_my_mindmaps)
        binding.topAppBar.title = title
        switchFragment(MindMapListFragment.newInstance(title))
        setCheckedNav(R.id.nav_my_mindmaps)
    }

    private fun openHome() {
        binding.topAppBar.title = getString(R.string.nav_recent)
        switchFragment(HomeDashboardFragment.newInstance())
        setCheckedNav(R.id.nav_recent)
    }

    private fun openNoteList() {
        binding.topAppBar.title = getString(R.string.nav_notes)
        switchFragment(NoteListFragment.newInstance())
        setCheckedNav(R.id.nav_notes)
    }

    private fun openSimplePage(pageKey: String) {
        binding.topAppBar.title = SimplePageFragment.resolveTitle(this, pageKey)
        switchFragment(SimplePageFragment.newInstance(pageKey))
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
        }
    }

    private fun setCheckedNav(navId: Int) {
        currentCheckedNavId = navId
        binding.bottomNavigation.menu.findItem(navId)?.isChecked = true
        binding.navigationRail.menu.findItem(navId)?.isChecked = true
    }

    private fun handleDestinationIntent(intent: Intent): Boolean {
        when (intent.getStringExtra(EXTRA_DESTINATION)) {
            DEST_RECENT -> {
                openHome()
                return true
            }
            DEST_NOTES -> {
                openNoteList()
                return true
            }
            DEST_MINDMAPS -> {
                openMindMapList()
                return true
            }
        }
        return false
    }

    companion object {
        const val EXTRA_DESTINATION = "extra_destination"
        const val DEST_RECENT = "recent"
        const val DEST_NOTES = "notes"
        const val DEST_MINDMAPS = "mindmaps"
    }
}
