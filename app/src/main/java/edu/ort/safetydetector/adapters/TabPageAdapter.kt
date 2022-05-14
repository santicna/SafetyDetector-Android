package edu.ort.safetydetector.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import edu.ort.safetydetector.fragments.AccountFragment
import edu.ort.safetydetector.fragments.StatsFragment
import edu.ort.safetydetector.fragments.UploadFragment

class TabPageAdapter(activity: FragmentActivity, private val tabCount: Int) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int {
        return tabCount
    }

    override fun createFragment(position: Int): Fragment {
        return when(position){
            0 -> StatsFragment()
            1 -> UploadFragment()
            2 -> AccountFragment()
            else -> StatsFragment()
        }
    }
}