package com.example.mymind

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.core.view.GravityCompat
import com.example.mymind.databinding.ActivityMainBinding
import com.example.mymind.ui.mindmap.MindMapListFragment
import com.example.mymind.ui.note.NoteListFragment
import com.example.mymind.ui.home.HomeDashboardFragment
import com.example.mymind.ui.simple.SimplePageFragment
import com.example.mymind.ui.trash.TrashActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var currentCheckedNavId: Int = R.id.nav_recent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbarAndDrawer()
        setupDrawerNavigation()

        if (savedInstanceState == null) {
            if (!handleDestinationIntent(intent)) {
                openHome()
                setCheckedNav(R.id.nav_recent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDestinationIntent(intent)
    }

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.topAppBar)
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.topAppBar,
            R.string.app_name,
            R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
    }

    private fun setupDrawerNavigation() {
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_recent -> openHome()
                R.id.nav_my_mindmaps -> openMindMapList(title = "我的导图")
                R.id.nav_notes -> openNoteList()
                R.id.nav_shared -> {
                    openSimplePage("已共享")
                    setCheckedNav(R.id.nav_shared)
                }
                R.id.nav_trash -> {
                    val returnDest = when (currentCheckedNavId) {
                        R.id.nav_my_mindmaps -> DEST_MINDMAPS
                        R.id.nav_notes -> DEST_NOTES
                        else -> DEST_RECENT
                    }
                    setCheckedNav(R.id.nav_trash)
                    startActivity(TrashActivity.createIntent(this, returnDestination = returnDest))
                }
                R.id.nav_settings -> openSimplePage("设置")
                R.id.nav_help -> openSimplePage("帮助")
                R.id.nav_about -> openSimplePage("关于")
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun openMindMapList(title: String) {
        binding.topAppBar.title = title
        switchFragment(MindMapListFragment.newInstance(title))
        setCheckedNav(R.id.nav_my_mindmaps)
    }

    private fun openHome() {
        binding.topAppBar.title = "最近"
        switchFragment(HomeDashboardFragment.newInstance())
        setCheckedNav(R.id.nav_recent)
    }

    private fun openNoteList() {
        binding.topAppBar.title = "笔记"
        switchFragment(NoteListFragment.newInstance())
        setCheckedNav(R.id.nav_notes)
    }

    private fun openSimplePage(title: String) {
        binding.topAppBar.title = title
        switchFragment(SimplePageFragment.newInstance(title))
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
        }
    }

    private fun setCheckedNav(navId: Int) {
        currentCheckedNavId = navId
        binding.navigationView.setCheckedItem(navId)
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
                openMindMapList(title = "我的导图")
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
