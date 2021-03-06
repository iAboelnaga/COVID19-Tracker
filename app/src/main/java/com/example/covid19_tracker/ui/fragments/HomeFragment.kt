package com.example.covid19_tracker.ui.fragments

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.covid19_tracker.R
import com.example.covid19_tracker.model.Country
import com.example.covid19_tracker.viewmodel.MainViewModel
import com.google.android.material.appbar.AppBarLayout
import kotlinx.android.synthetic.main.fragment_home.*
import com.example.covid19_tracker.model.WorldState
import com.parassidhu.coronavirusapp.util.*

class HomeFragment:Fragment(), AppBarLayout.OnOffsetChangedListener,
    BaseRecyclerViewAdapter.OnEvent {

    protected lateinit var rootView: View
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: BaseRecyclerViewAdapter
    private lateinit var viewModel: MainViewModel
    var worldData: WorldState = WorldState(100, "", "","")
    var dataList : MutableList<Country> = ArrayList<Country>()
    private val FIRST_RUN = "first_run_flag"

    companion object {
        var TAG = HomeFragment::class.java.simpleName
        const val ARG_POSITION: String = "positioin"
        fun newInstance(): HomeFragment {
            var fragment =
                HomeFragment();
            val args = Bundle()
            args.putInt(ARG_POSITION, 1)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        onCreateComponent()
    }

    private fun onCreateComponent() {
        adapter =
            BaseRecyclerViewAdapter(
                dataList,
                context,
                this
            )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_home, container, false);
        initView()
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showLoading(true)
        setListeners()
    }

    private fun initView() {
        initializeRecyclerView()
        //setupWorldStats(worldData)
    }

    private fun initializeRecyclerView() {
        recyclerView = rootView.findViewById(R.id.countryWiseRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        if(checkConnectivity()){
            getNewData()
            getNewWorldRecords()
        }else{
            if (checkFirstRun()){
                Toast.makeText(context,"Internet Connection is a must in first time , restart app",Toast.LENGTH_LONG).show()
            }else{
                getSavedData()
                getWorldRecordsSavedData()
                Toast.makeText(context, "Check network connection", Toast.LENGTH_SHORT).show()
            }
        }

    }

    override fun onResume() {
        super.onResume()
        appBarLayout?.addOnOffsetChangedListener(this)
    }

    override fun onPause() {
        super.onPause()
        appBarLayout?.removeOnOffsetChangedListener(this)
    }

    override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
        swipeToRefresh.isEnabled = verticalOffset == 0 && !searchBar.isVisible
    }

    private fun setListeners() {
        searchImageView.setOnClickListener {
            showSearch(true)
            runInHandler(200) { showKeyboard(searchEditText, true) }
            searchEditText.requestFocus()
        }

        backButton.setOnClickListener {
            showSearch(false)
            showKeyboard(searchEditText, false)
            searchEditText.setText("")
        }

        searchEditText.doAfterTextChanged { text: Editable? ->
            adapter.search(text.toString())
        }

        swipeToRefresh.setOnRefreshListener {
            if(checkConnectivity()){
                adapter.clear()
                showLoading(true)
                makeApiCalls()
                adapter.notifyDataSetChanged()

            }else{
                Toast.makeText(context,"No Internet Connection",Toast.LENGTH_LONG).show()
            }
            swipeToRefresh.isRefreshing = false

        }

        hamburgerImageView.setOnClickListener {
            //TODO
        }
    }

    private fun showSearch(flag: Boolean) {
        searchBar.isInvisible = !flag
        backButton.isInvisible = !flag
        hamburgerImageView.isInvisible = flag
        searchImageView.isInvisible = flag
        toolbarViews.isVisible = !flag
        bannerImage.isVisible = !flag
        titleLogo.isVisible = !flag
        swipeToRefresh.isEnabled = !flag

        if (!flag) {
            adapter.search("")
        }
    }

    fun scrollToTop() {
        countryWiseRecyclerView?.smoothScrollToPosition(0)
        appBarLayout?.setExpanded(true, true)
    }

    fun handleBackPress(): Boolean {
        if (searchBar.isVisible) {
            showSearch(false)
            return true
        }

        return false
    }

    private fun makeApiCalls() {
        getNewData()
        getNewWorldRecords()
    }

    fun provideBarWeights(response: WorldState): Triple<Float?, Float?, Float?> {
        try {
            var yellowVal = response.total_cases.replace(",", "").toFloat()
            var greenVal = response.total_recovered.replace(",", "").toFloat()
            var redVal = response.total_deaths.replace(",", "").toFloat()

                while (yellowVal > 1f && greenVal > 1f && redVal > 1f) {
                    yellowVal /= 2f
                    greenVal /= 2f
                    redVal /= 2f
                }


            return Triple(yellowVal, greenVal, redVal)
        } catch (e: Exception) { // If API response is incorrect
            e.printStackTrace()
            return Triple(1f, 1f, 1f)
        }
    }

    fun update(countries: List<Country>){
        if (countries.isNotEmpty()) {
            val pref = activity?.getSharedPreferences("sub_country",Context.MODE_PRIVATE)
            val str = pref?.getString("country","n/a")
            dataList.clear()
            for(country in countries){
                if (country.country_name.equals(str,true)){
                    dataList.add(0,country)
                }else{
                    dataList.add(country)
                }
            }
            adapter.setCovid(countries)
            showLoading(false)
        }
    }

    private fun showLoading(flag: Boolean) {
        countryWiseRecyclerView.isVisible = !flag
        toolbarViews.isVisible = !flag

        if (flag)
            shimmerLoading.start()
        else
            shimmerLoading.stop()
    }

    fun setupWorldStats(response: WorldState) {
        val weight = provideBarWeights(response)

        Glide.with(this).load(R.drawable.yellow_bar).apply(cornerRadius(2)).into(yellowBar)
        Glide.with(this).load(R.drawable.green_bar).apply(cornerRadius(2)).into(greenBar)
        Glide.with(this).load(R.drawable.red_bar).apply(cornerRadius(2)).into(redBar)

        yellowBar.setWeight(weight.first!!)
        greenBar.setWeight(weight.second!!)
        redBar.setWeight(weight.third!!)

        barContainer.weightSum = weight.first!! + weight.second!! + weight.third!!

        println(response.total_cases)
        response.apply {
            confirmedCount.text = response.total_cases
            recoveredCount.text = response.total_recovered
            deathCount.text = response.total_deaths
        }
    }

    override fun logEvent(query: String) {
        val params = Bundle()
        params.putString("query", query)
    }


    private fun checkConnectivity(): Boolean {
        val connectivityManager =
            context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        val isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting
        return isConnected
    }

    private fun checkFirstRun(): Boolean {
        val pref = activity?.getPreferences(Context.MODE_PRIVATE)
        val isFirstRun = pref?.getBoolean(FIRST_RUN, true)
        return isFirstRun!!
    }
    private  fun getNewWorldRecords(){
        viewModel.getNewWorldData().observe(this, Observer<WorldState> { data ->
            // update UI
            if(data != null){
                setupWorldStats(data)
            }else{
                println("Error Fetching Data")
            }
        })
    }

    private fun getWorldRecordsSavedData(){
        viewModel.getSavedWorldState().observe(this, Observer {
            if (it != null){
                setupWorldStats(it)
            }else{
                getWorldRecordsSavedData()
            }
        })
    }

    private fun getNewData(){
        viewModel.getNewData().observe(this, Observer<List<Country>> { countries ->
            // update UI
            if(checkFirstRun()){
                val pref =    activity?.getPreferences(Context.MODE_PRIVATE)
                val editor = pref?.edit()
                editor?.putBoolean(FIRST_RUN, false)
                editor?.apply()
            }
            if (countries != null) {
                var list = countries
                adapter.clear()
                update(list)
                adapter.notifyDataSetChanged()
            } else {
                getSavedData()
                Toast.makeText(context, "Error Fetching New Data", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun getSavedData(){

        viewModel.getSavedData().observe(this, Observer<List<Country>> { countries ->
            // update UI
            if(countries != null){
                update(countries)
            } else {
                Toast.makeText(context, "Error Fetching Data", Toast.LENGTH_LONG).show()
            }

        })
    }
}